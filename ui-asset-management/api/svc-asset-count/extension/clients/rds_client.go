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
	"strings"
	"svc-asset-count-layer/models"
	"sync"

	"github.com/georgysavva/scany/sqlscan"
	_ "github.com/go-sql-driver/mysql"
)

type RdsClient struct {
	secretIdPrefix string
	secretsClient  *SecretsClient
	rdsClientCache sync.Map
}

func NewRdsClient(secretsClient *SecretsClient, secretIdPrefix string) *RdsClient {
	return &RdsClient{
		secretIdPrefix: secretIdPrefix,
		secretsClient:  secretsClient,
	}
}

func (r *RdsClient) CreateNewClient(ctx context.Context, tenantId string) (*sql.DB, error) {
	// check if the client is already created
	if db, ok := r.rdsClientCache.Load(tenantId); ok {
		return db.(*sql.DB), nil // type assert to *sql.DB
	}

	rdsCredentials, err := r.secretsClient.GetRdsSecret(ctx, r.secretIdPrefix, tenantId)
	if err != nil {
		return nil, fmt.Errorf("failed to get RDS credentials: %v", err)
	}
	if rdsCredentials == nil {
		return nil, fmt.Errorf("RDS credentials are nil")
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
	db, err := sql.Open("mysql", dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to open database connection: %v", err)
	}

	// check if the database is reachable
	err = db.Ping()
	if err != nil {
		return nil, fmt.Errorf("failed to ping database: %v", err)
	}

	fmt.Println("connected to rds successfully!")
	r.rdsClientCache.Store(tenantId, db) // store db in cache
	return db, nil
}

func (r *RdsClient) GetAllTargetTypes(ctx context.Context, tenantId, dataSource string) (*[]models.TargetTableProjection, error) {
	dbClient, err := r.CreateNewClient(ctx, tenantId)
	if err != nil {
		return nil, err
	}
	fmt.Println("getting all target types from rds")

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
	if err := sqlscan.Select(ctx, dbClient, &policies, query, args...); err != nil {
		return nil, err
	}

	return &policies, nil
}

func (r *RdsClient) GetConfiguredTargetTypes(ctx context.Context, agTargetTypes []string, domain, provider, tenantId string) (*[]models.TargetTableProjection, error) {
	dbClient, err := r.CreateNewClient(ctx, tenantId)
	if err != nil {
		return nil, err
	}
	fmt.Println("getting configured target types by asset group")

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
	args := []interface{}{}

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
	if err := sqlscan.Select(ctx, dbClient, &targetTypes, formattedQuery, args...); err != nil {
		return nil, err
	}

	return &targetTypes, nil
}

func (r *RdsClient) GetCloudProviders(ctx context.Context, tenantId string) (*[]models.PluginsTableProjection, error) {
	dbClient, err := r.CreateNewClient(ctx, tenantId)
	if err != nil {
		return nil, err
	}
	fmt.Println("fetching cloud providers from plugins table")

	query := `
	SELECT source FROM plugins WHERE type='Cloud Provider';
	`

	var plugins []models.PluginsTableProjection
	if err := sqlscan.Select(ctx, dbClient, &plugins, query); err != nil {
		return nil, err
	}

	return &plugins, nil
}

func (r *RdsClient) CloseClient() {
	// close all connections in the cache
	r.rdsClientCache.Range(func(key, value interface{}) bool {
		db := value.(*sql.DB)
		db.Close()
		r.rdsClientCache.Delete(key)
		return true
	})
}
