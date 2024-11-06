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
	"svc-asset-violations-layer/models"
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

func (r *RdsClient) GetAllPoliciesCount(ctx context.Context, tenantId, targetType string) (int, error) {
	rdsClient, err := r.CreateNewRdsClient(ctx, tenantId)
	if err != nil {
		return 0, fmt.Errorf("failed to create rds client %w", err)
	}

	log.Println("getting policy count from rds for targetType: %s", targetType)
	query := `
		SELECT 
			count(p.policyId) as count
		FROM 
			cf_PolicyTable p
		LEFT JOIN 
			cf_PolicyParams pp ON p.policyId = pp.policyId 
			AND pp.paramKey = 'pluginType'
		LEFT JOIN 
			cf_Accounts a ON pp.paramValue = a.platform
		WHERE 
			p.targetType = ?
			AND ((a.platform IS NULL AND pp.policyId IS NULL)
            OR (a.platform IS NOT NULL AND pp.policyId IS NOT NULL))
		ORDER BY 
			p.policyId;
	`

	var args []interface{}
	args = append(args, targetType)
	var policyCount int

	row := rdsClient.QueryRow(query, args...)

	if err := row.Scan(&policyCount); err != nil {
		return 0, fmt.Errorf("failed to get policy count from rds %w", err)
	}

	return policyCount, nil
}

func (r *RdsClient) GetEnabledPolicies(ctx context.Context, tenantId, targetType string) ([]models.PolicyRdsResult, error) {
	rdsClient, err := r.CreateNewRdsClient(ctx, tenantId)
	if err != nil {
		return nil, fmt.Errorf("failed to create rds client %w", err)
	}

	log.Println("getting policies from rds")
	query := `
		SELECT 
			p.policyId,
			p.policyDisplayName,
			p.category,
			p.severity
		FROM 
			cf_PolicyTable p
		LEFT JOIN 
			cf_PolicyParams pp ON p.policyId = pp.policyId 
			AND pp.paramKey = 'pluginType'
		LEFT JOIN 
			cf_Accounts a ON pp.paramValue = a.platform
		WHERE 
			p.status = 'ENABLED' 
			AND p.targetType = ?
			AND ((a.platform IS NULL AND pp.policyId IS NULL)
            OR (a.platform IS NOT NULL AND pp.policyId IS NOT NULL))
		ORDER BY 
			p.policyId;
	`

	var args []interface{}
	args = append(args, targetType)
	var policies []models.PolicyRdsResult
	if err := sqlscan.Select(ctx, rdsClient, &policies, query, args...); err != nil {
		return nil, fmt.Errorf("failed to get policies from rds %w", err)
	}

	return policies, nil
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
