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
	"os"
	"strconv"
	"svc-asset-details-layer/models"
)

type Configuration struct {
	Region                   string
	TenantConfigTable        string
	TenantConfigPartitionKey string
	EnableExtension          bool
	RdsHost                  string
	RdsPort                  string
	RdsDbName                string
	RdsCredentials           models.RdsSecret
	RdsSecretName            string
}

func LoadConfigurationDetails(ctx context.Context) *Configuration {
	enableExtensionStr := os.Getenv("ENABLE_EXTENSION")
	enableExtension, err := strconv.ParseBool(enableExtensionStr)
	if err != nil {
		// When we deploy the lambda + extension, set the default runtime to enable extension
		enableExtension = true
	}
	region := os.Getenv("REGION")
	tenantConfigTable := os.Getenv("TENANT_CONFIG_TABLE")
	tenantConfigPartitionKey := os.Getenv("TENANT_CONFIG_PARTITION_KEY")
	rdsSecretName := os.Getenv("RDS_SECRET_NAME")
	rdsHost := os.Getenv("RDS_HOST")
	rdsPort := os.Getenv("RDS_PORT")
	rdsDbName := os.Getenv("RDS_DB_NAME")

	secretsClient := NewSecretsClient(region)
	rdsCredentials, _ := secretsClient.GetRdsSecret(ctx, rdsSecretName)

	return &Configuration{
		EnableExtension:          enableExtension,
		Region:                   region,
		TenantConfigTable:        tenantConfigTable,
		TenantConfigPartitionKey: tenantConfigPartitionKey,
		RdsSecretName:            rdsSecretName,
		RdsHost:                  rdsHost,
		RdsPort:                  rdsPort,
		RdsDbName:                rdsDbName,
		RdsCredentials:           *rdsCredentials,
	}
}
