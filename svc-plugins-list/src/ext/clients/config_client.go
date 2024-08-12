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
	"cache-layer/src/ext/models"
	"context"
	"os"
)

type Configuration struct {
	Region                   string
	TenantConfigTable        string
	TenantConfigPartitionKey string
	TenantId                 string
	RdsCredentials           models.RdsSecret
}

var (
	secretsName = "paladincloud/secret/"
)

func LoadConfigurationDetails(ctx context.Context) *Configuration {
	region := os.Getenv("REGION")
	tenantConfigTable := os.Getenv("TENANT_CONFIG_TABLE")
	tenantConfigPartitionKey := os.Getenv("TENANT_CONFIG_PARTITION_KEY")
	tenantId := os.Getenv("TENANT_ID")

	secretsClient := NewSecretsClient(region)
	rdsCredentials, _ := secretsClient.GetRdsSecret(ctx, secretsName+tenantId)

	return &Configuration{
		Region:                   region,
		TenantConfigTable:        tenantConfigTable,
		TenantConfigPartitionKey: tenantConfigPartitionKey,
		TenantId:                 tenantId,
		RdsCredentials:           *rdsCredentials,
	}
}
