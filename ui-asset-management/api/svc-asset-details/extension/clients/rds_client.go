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
	"svc-asset-details-layer/models"

	"github.com/georgysavva/scany/sqlscan"
	_ "github.com/go-sql-driver/mysql"
)

type RdsClient struct {
	secretIdPrefix string
	secretsClient  *SecretsClient
	rdsClientCache map[string]*sql.DB
}

func NewRdsClient(secretsClient *SecretsClient, secretIdPrefix string) *RdsClient {
	return &RdsClient{
		secretIdPrefix: secretIdPrefix,
		secretsClient:  secretsClient,
		rdsClientCache: make(map[string]*sql.DB),
	}
}

func (r *RdsClient) CreateNewClient(ctx context.Context, tenantId string) *sql.DB {
	// check if the client is already created
	if db, ok := r.rdsClientCache[tenantId]; ok {
		return db
	}

	rdsCredentials, _ := r.secretsClient.GetRdsSecret(ctx, r.secretIdPrefix, tenantId)
	var (
		dbUser     = rdsCredentials.DbUsername
		dbPassword = rdsCredentials.DbPassword
		dbHost     = rdsCredentials.DbHost
		dbPort     = rdsCredentials.DbPort
		dbName     = rdsCredentials.DbName
	)

	// Data Plugin Name (DSN)
	dsn := fmt.Sprintf("%s:%s@tcp(%s:%s)/%s", dbUser, dbPassword, dbHost, dbPort, dbName)

	// Open a connection to the database
	db, err := sql.Open("mysql", dsn)
	if err != nil {
		return nil
	}

	// Check if the database is reachable
	err = db.Ping()
	if err != nil {
		return nil
	}

	fmt.Println("Connected to the database successfully!")
	return db
}

func (r *RdsClient) FetchMandatoryTags(ctx context.Context, tenantId string) ([]models.Tag, error) {
	dbClient := r.CreateNewClient(ctx, tenantId)

	fmt.Println("Getting Plugins List from RDS")
	query := `
		select opt.optionName as tagName
                from pac_v2_ui_options opt join pac_v2_ui_filters fil on opt.filterId= fil.filterId 
                and opt.optionValue like '%tags%' and fil.filterName='AssetListing';
	`
	var tags []models.Tag
	if err := sqlscan.Select(ctx, dbClient, &tags, query); err != nil {
		return nil, err
	}

	return tags, nil
}

func (r *RdsClient) closeClient(db *sql.DB) {
	// Close all connections in the cache
	for s := range r.rdsClientCache {
		r.rdsClientCache[s].Close()
		delete(r.rdsClientCache, s)
	}
}
