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
	"svc-plugins-list-layer/models"
	"testing"
)

func TestConfigurationDetails(t *testing.T) {
	configuration := &Configuration{
		Region:                   "us-east-1",
		TenantId:                 "[TENANT_ID]",
		TenantConfigTable:        "[DYNAMODB_TENANT_CONFIG_TABLE]",
		TenantConfigPartitionKey: "[DYNAMODB_TENANT_CONFIG_PARTITION_KEY]",
		RdsCredentials: models.RdsSecret{
			DbUsername: "DB_USERNAME",
			DbPassword: "DB_PASSWORD",
			DbHost:     "DB_HOST",
			DbPort:     "DB_PORT",
			DbName:     "DB_NAME",
		},
	}

	println(configuration)
}