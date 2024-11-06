/*
 * Copyright (c) 2024 Paladin Cloud, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package clients

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"strings"
	"svc-asset-state-count-layer/models"
	"sync"

	"github.com/georgysavva/scany/sqlscan"
	_ "github.com/go-sql-driver/mysql"
)

type RdsClient struct {
	secretIdPrefix string
	secretsClient  *SecretsClient
	rdsClientCache sync.Map
}

func NewRdsClient(secretsClient *SecretsClient, secretIdPrefix string) (*RdsClient, error) {
	if secretsClient == nil {
		return nil, fmt.Errorf("secretsClient cannot be nil")
	}
	if secretIdPrefix == "" {
		return nil, fmt.Errorf("secretIdPrefix cannot be empty")
	}

	return &RdsClient{
		secretIdPrefix: secretIdPrefix,
		secretsClient:  secretsClient,
	}, nil
}

func (r *RdsClient) CreateNewRdsClient(ctx context.Context, tenantId string) (*sql.DB, error) {
	// check if the client is already created
	if db, ok := r.rdsClientCache.Load(tenantId); ok {
		if dbClient, ok := db.(*sql.DB); ok {
			return dbClient, nil
		}

		return nil, fmt.Errorf("invalid rds client type in cache")
	}

	rdsCredentials, err := r.secretsClient.GetRdsSecret(ctx, r.secretIdPrefix, tenantId)
	if err != nil {
		return nil, fmt.Errorf("failed to get rds credentials %w", err)
	}

	if rdsCredentials == nil {
		return nil, fmt.Errorf("rds credentials are missing")
	}

	var (
		dbUser     = rdsCredentials.DbUsername
		dbPassword = rdsCredentials.DbPassword
		dbHost     = rdsCredentials.DbHost
		dbPort     = rdsCredentials.DbPort
		dbName     = rdsCredentials.DbName
	)

	// Data Source Name (DSN)
	dsn := fmt.Sprintf("%s:%s@tcp(%s:%s)/%s", dbUser, dbPassword, dbHost, dbPort, dbName)

	// open a connection to the database
	rds, err := sql.Open("mysql", dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to open database connection %w", err)
	}

	// check if the database is reachable
	err = rds.Ping()
	if err != nil {
		return nil, fmt.Errorf("database ping failed %w", err)
	}

	log.Printf("connected to rds successfully for tenantId [%s]\n", tenantId)
	r.rdsClientCache.Store(tenantId, rds) // store rds in cache
	return rds, nil
}

func (r *RdsClient) GetAllTargetTypes(ctx context.Context, tenantId, dataSource string) (*[]models.TargetTableProjection, error) {
	rdsClient, err := r.CreateNewRdsClient(ctx, tenantId)
	if err != nil {
		return nil, fmt.Errorf("failed to create rds client %w", err)
	}

	query := `
	SELECT 
		DISTINCT targetName as type,
		displayName as displayName, 
		category, 
		dataSourceName as provider 
	FROM 
		cf_Target 
	WHERE
		(status = 'active' OR status = 'enabled')
	`
	var args []interface{}
	if dataSource != "" {
		query += " AND lower(dataSourceName) = ?"
		args = append(args, strings.ToLower(dataSource))
	}
	query += " AND dataSourceName IN (select distinct platform from cf_Accounts where accountStatus='configured') ORDER BY LOWER (displayName) ASC;"

	var policies []models.TargetTableProjection
	if err2 := sqlscan.Select(ctx, rdsClient, &policies, query, args...); err2 != nil {
		return nil, fmt.Errorf("failed to fetch target types %w", err2)
	}

	return &policies, nil
}

func (r *RdsClient) GetConfiguredTargetTypes(ctx context.Context, agTargetTypes []string, domain, provider, tenantId string) (*[]models.TargetTableProjection, error) {
	rdsClient, err := r.CreateNewRdsClient(ctx, tenantId)
	if err != nil {
		return nil, fmt.Errorf("failed to create rds client %w", err)
	}

	query := `
	SELECT 
		DISTINCT targetName as type, 
		displayName as displayName, 
		category as category,
		domain as domain, 
		dataSourceName as provider 
	FROM 
		cf_Target
	WHERE 
		(status = 'active' OR status = 'enabled') 
	AND 
		targetName in ('%s')
	`
	placeholders := make([]string, len(agTargetTypes))
	var args []interface{}

	for i, targetType := range agTargetTypes {
		placeholders[i] = "?"
		args = append(args, targetType)
	}
	query += " AND targetName IN (" + strings.Join(placeholders, ",") + ")"

	if domain != "" {
		query += " AND lower(domain) = ?"
		args = append(args, strings.TrimSpace(strings.ToLower(domain)))
	}
	if provider != "" {
		query += " AND lower(dataSourceName) = ?"
		args = append(args, strings.TrimSpace(strings.ToLower(provider)))
	}

	query += " AND dataSourceName in (select distinct platform from cf_Accounts WHERE accountStatus='configured') ORDER BY LOWER (displayName) ASC;"

	formattedQuery := fmt.Sprintf(query, strings.Join(agTargetTypes, "','"))

	var targetTypes []models.TargetTableProjection
	if err2 := sqlscan.Select(ctx, rdsClient, &targetTypes, formattedQuery, args...); err2 != nil {
		return nil, fmt.Errorf("failed to fetch target types %w", err2)
	}

	return &targetTypes, nil
}

func (r *RdsClient) GetCloudProviders(ctx context.Context, tenantId string) (*[]models.PluginsTableProjection, error) {
	rdsClient, err := r.CreateNewRdsClient(ctx, tenantId)
	if err != nil {
		return nil, fmt.Errorf("failed to create rds client %w", err)
	}

	query := `
	SELECT source FROM plugins WHERE type='Cloud Provider';
	`

	var plugins []models.PluginsTableProjection
	if err2 := sqlscan.Select(ctx, rdsClient, &plugins, query); err2 != nil {
		return nil, fmt.Errorf("failed to fetch cloud providers %w", err2)
	}

	return &plugins, nil
}

func (r *RdsClient) CloseClient() {
	// close all connections in the cache
	r.rdsClientCache.Range(func(key, value interface{}) bool {
		rds := value.(*sql.DB)
		rds.Close()
		r.rdsClientCache.Delete(key)
		return true
	})
}
