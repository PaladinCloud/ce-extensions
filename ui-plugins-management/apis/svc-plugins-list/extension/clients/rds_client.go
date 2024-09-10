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
	"svc-plugins-list-layer/models"
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

func (r *RdsClient) GetPluginsList(ctx context.Context, tenantId string, flags models.PluginFeatureFlags) (*models.Plugins, error) {
	println("Getting Plugins List from RDS")
	query := `
		SELECT 
			p.source,
			p.name,
			p.type,
			p.description,
			p.iconURL,
			CAST(p.isLegacy AS DECIMAL) AS isLegacy,
			CAST(p.disablePolicyActions AS DECIMAL) AS disablePolicyActions,
			CAST(p.isInbound AS DECIMAL) AS isInbound,
			CAST((
				p.isCloud = true
				AND EXISTS (
					SELECT 1
					FROM tenant_accounts a
					WHERE a.tenant_id = '%s'
					AND a.source IN (
						SELECT cp.source
						FROM plugins cp
						WHERE cp.isComposite = true
					)
					AND p.source != a.source
				)
			) AS DECIMAL) AS isHidden
		FROM 
			plugins p;
	`
	formattedQuery := fmt.Sprintf(query, tenantId)

	var plugins []models.Plugin
	if err := sqlscan.Select(ctx, r.db, &plugins, formattedQuery); err != nil {
		return nil, err
	}

	var inboundPlugins []models.Plugin
	var outboundPlugins []models.Plugin
	for i := range plugins {
		plugin := &plugins[i]
		plugin.Flags = flags[plugin.Source]

		if plugin.IsInbound {
			inboundPlugins = append(inboundPlugins, *plugin)
		} else {
			outboundPlugins = append(outboundPlugins, *plugin)
		}
	}

	return &models.Plugins{
		Inbound:  inboundPlugins,
		Outbound: outboundPlugins,
	}, nil
}

func (r *RdsClient) Close() error {
	return r.db.Close()
}
