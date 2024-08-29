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

	"github.com/georgysavva/scany/v2/sqlscan"
	_ "github.com/go-sql-driver/mysql"
)

type RdsClient struct {
	db *sql.DB
}

func NewRdsClient(configuration *Configuration) *RdsClient {
	var (
		dbUser     = configuration.RdsCredentials.DbUsername
		dbPassword = configuration.RdsCredentials.DbPassword
		dbHost     = configuration.RdsCredentials.DbHost
		dbPort     = configuration.RdsCredentials.DbPort
		dbName     = configuration.RdsCredentials.DbName
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

func (r *RdsClient) GetPluginName(ctx context.Context, source string) (*string, error) {
	println("Getting Plugin name from RDS")
	query := `
						SELECT p.name 
						FROM plugins p 
						WHERE p.source = %s;
`
	query = fmt.Sprintf(query, source)

	var pluginName string
	if err := sqlscan.Select(ctx, r.db, &pluginName, query); err != nil {
		return nil, err
	}

	return &pluginName, nil
}

func (r *RdsClient) Close() error {
	return r.db.Close()
}
