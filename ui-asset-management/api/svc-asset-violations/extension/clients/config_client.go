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
	"fmt"
	"log"
	"os"
	"strconv"
)

type Configuration struct {
	EnableExtension               bool
	AssumeRoleArn                 string
	Region                        string
	TenantConfigTable             string
	TenantConfigTablePartitionKey string
	SecretIdPrefix                string
}

func LoadConfigurationDetails() *Configuration {
	enableExtensionStr := os.Getenv("ENABLE_EXTENSION")
	enableExtension, err := strconv.ParseBool(enableExtensionStr)
	if err != nil {
		// When we deploy the lambda + extension, set the default runtime to enable extension
		fmt.Println("ENABLE_EXTENSION environment variable not set, defaulting to true")
		enableExtension = true
	}

	region := os.Getenv("REGION")
	if region == "" {
		log.Fatalf("environment variable REGION must be set")
	}

	assumeRoleArn := os.Getenv("ASSUME_ROLE_ARN")
	if assumeRoleArn == "" {
		log.Fatalf("environment variable ASSUME_ROLE_ARN must be set")
	}

	tenantConfigTable := os.Getenv("TENANT_CONFIG_TABLE")
	if tenantConfigTable == "" {
		log.Fatalf("environment variable TENANT_CONFIG_TABLE must be set")
	}

	tenantConfigTablePartitionKey := os.Getenv("TENANT_CONFIG_TABLE_PARTITION_KEY")
	if tenantConfigTablePartitionKey == "" {
		log.Fatalf("environment variable TENANT_CONFIG_TABLE_PARTITION_KEY must be set")
	}

	secretIdPrefix := os.Getenv("SECRET_NAME_PREFIX")
	if secretIdPrefix == "" {
		log.Fatalf("environment variable SECRET_NAME_PREFIX must be set")
	}

	return &Configuration{
		EnableExtension:               enableExtension,
		AssumeRoleArn:                 assumeRoleArn,
		Region:                        region,
		TenantConfigTable:             tenantConfigTable,
		TenantConfigTablePartitionKey: tenantConfigTablePartitionKey,
		SecretIdPrefix:                secretIdPrefix,
	}
}
