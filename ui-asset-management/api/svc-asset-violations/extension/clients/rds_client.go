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

	"github.com/georgysavva/scany/sqlscan"
	_ "github.com/go-sql-driver/mysql"
)

type RdsClient struct {
	db *sql.DB
}

func NewRdsClient(configuration *Configuration) *RdsClient {
	var (
		dbUser     = configuration.RdsCredentials.DbUsername
		dbPassword = configuration.RdsCredentials.DbPassword
		dbHost     = configuration.RdsHost
		dbPort     = configuration.RdsPort
		dbName     = configuration.RdsDbName
	)

	// Data Plugin Name (DSN)
	dsn := fmt.Sprintf("%s:%s@tcp(%s:%s)/%s", dbUser, dbPassword, dbHost, dbPort, dbName)

	// Open a connection to the database
	db, err := sql.Open("mysql", dsn)
	if err != nil {
		return nil
	}
	//defer db.Close()

	// Check if the database is reachable
	err = db.Ping()
	if err != nil {
		return nil
	}

	fmt.Println("Connected to the database successfully!")
	return &RdsClient{
		db: db,
	}
}

func (r *RdsClient) GetPolicies(ctx context.Context, targetType string) ([]models.Policy, error) {
	fmt.Println("Getting Plugins List from RDS")
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
	if err := sqlscan.Select(ctx, r.db, &policies, formattedQuery); err != nil {
		return nil, err
	}

	return policies, nil
}

func (r *RdsClient) Close() error {
	return r.db.Close()
}
