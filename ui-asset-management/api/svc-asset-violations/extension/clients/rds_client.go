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
	"svc-asset-violations-layer/models"
	"sync"

	"github.com/georgysavva/scany/sqlscan"
	_ "github.com/go-sql-driver/mysql"
)

type RdsClient struct {
	secretIdPrefix string
	secretsClient  *SecretsClient
	rdsClientCache sync.Map // Replaced with sync.Map
}

func NewRdsClient(secretsClient *SecretsClient, secretIdPrefix string) *RdsClient {
	return &RdsClient{
		secretIdPrefix: secretIdPrefix,
		secretsClient:  secretsClient,
	}
}

func (r *RdsClient) CreateNewClient(ctx context.Context, tenantId string) *sql.DB {
	// check if the client is already created
	if db, ok := r.rdsClientCache.Load(tenantId); ok {
		return db.(*sql.DB) // type assert to *sql.DB
	}

	rdsCredentials, _ := r.secretsClient.GetRdsSecret(ctx, r.secretIdPrefix, tenantId)

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
		return nil
	}

	// check if the database is reachable
	err = db.Ping()
	if err != nil {
		return nil
	}

	fmt.Println("connected to rds successfully!")
	r.rdsClientCache.Store(tenantId, db) // store db in cache
	return db
}

func (r *RdsClient) GetPolicies(ctx context.Context, tenantId, targetType string) ([]models.Policy, error) {
	dbClient := r.CreateNewClient(ctx, tenantId)
	fmt.Println("getting policies from rds")

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
			AND p.targetType = '%s'
			AND ((a.platform IS NULL AND pp.policyId IS NULL)
            OR (a.platform IS NOT NULL AND pp.policyId IS NOT NULL))
		ORDER BY 
			p.policyId;
	`
	formattedQuery := fmt.Sprintf(query, targetType)

	var policies []models.Policy
	if err := sqlscan.Select(ctx, dbClient, &policies, formattedQuery); err != nil {
		return nil, err
	}

	return policies, nil
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
