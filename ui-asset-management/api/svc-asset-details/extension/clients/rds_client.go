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
	"svc-asset-details-layer/models"
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
	return &RdsClient{
		secretIdPrefix: secretIdPrefix,
		secretsClient:  secretsClient,
	}, nil
}

func (r *RdsClient) CreateNewClient(ctx context.Context, tenantId string) (*sql.DB, error) {
	// check if the client is already created
	if db, ok := r.rdsClientCache.Load(tenantId); ok {
		return db.(*sql.DB), nil // type assert to *sql.DB
	}

	rdsCredentials, _ := r.secretsClient.GetRdsSecret(ctx, r.secretIdPrefix, tenantId)
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
		return nil, nil
	}

	// check if the database is reachable
	err = db.Ping()
	if err != nil {
		return nil, nil
	}

	fmt.Println("connected to rds successfully!")
	r.rdsClientCache.Store(tenantId, db) // store db in cache
	return db, nil
}

func (r *RdsClient) FetchMandatoryTags(ctx context.Context, tenantId string) ([]models.Tag, error) {
	dbClient, err := r.CreateNewClient(ctx, tenantId)
	if err != nil {
		return nil, err
	}

	log.Println("getting mandatory tags from rds")
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

func (r *RdsClient) CloseClient() {
	// close all connections in the cache
	r.rdsClientCache.Range(func(key, value interface{}) bool {
		db := value.(*sql.DB)
		db.Close()
		r.rdsClientCache.Delete(key)
		return true
	})
}
