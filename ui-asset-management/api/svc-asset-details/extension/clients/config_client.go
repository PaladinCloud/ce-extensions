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
	"os"
	"strconv"
)

type Configuration struct {
	Region                        string
	TenantConfigTable             string
	TenantConfigTablePartitionKey string
	EnableExtension               bool
	SecretNamePrefix              string
}

func LoadConfigurationDetails() *Configuration {
	enableExtensionStr := os.Getenv("ENABLE_EXTENSION")
	enableExtension, err := strconv.ParseBool(enableExtensionStr)
	if err != nil {
		// When we deploy the lambda + extension, set the default runtime to enable extension
		enableExtension = true
	}
	region := os.Getenv("REGION")
	tenantConfigTable := os.Getenv("TENANT_CONFIG_TABLE")
	tenantConfigTablePartitionKey := os.Getenv("TENANT_CONFIG_TABLE_PARTITION_KEY")
	secretNamePrefix := os.Getenv("SECRET_NAME_PREFIX")

	configuration := &Configuration{
		EnableExtension:               enableExtension,
		Region:                        region,
		TenantConfigTable:             tenantConfigTable,
		TenantConfigTablePartitionKey: tenantConfigTablePartitionKey,
		SecretNamePrefix:              secretNamePrefix,
	}

	fmt.Printf("Configuration: %v\n", *configuration)

	return configuration
}
