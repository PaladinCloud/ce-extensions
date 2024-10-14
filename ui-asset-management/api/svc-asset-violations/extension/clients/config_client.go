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
	EnableExtension         bool
	AssumeRoleArn           string
	Region                  string
	TenantConfigOutputTable string
	tenantTablePartitionKey string
	SecretIdPrefix          string
}

func LoadConfigurationDetails() *Configuration {
	enableExtensionStr := os.Getenv("ENABLE_EXTENSION")
	enableExtension, err := strconv.ParseBool(enableExtensionStr)
	if err != nil {
		// When we deploy the lambda + extension, set the default runtime to enable extension
		fmt.Println("ENABLE_EXTENSION environment variable not set, defaulting to true")
		enableExtension = true
	}

	region := getEnvVariable("REGION")
	assumeRoleArn := getEnvVariable("ASSUME_ROLE_ARN")
	tenantConfigOutputTable := getEnvVariable("TENANT_CONFIG_OUTPUT_TABLE")
	tenantTablePartitionKey := getEnvVariable("TENANT_TABLE_PARTITION_KEY")
	secretIdPrefix := getEnvVariable("SECRET_NAME_PREFIX")

	return &Configuration{
		EnableExtension:         enableExtension,
		AssumeRoleArn:           assumeRoleArn,
		Region:                  region,
		TenantConfigOutputTable: tenantConfigOutputTable,
		tenantTablePartitionKey: tenantTablePartitionKey,
		SecretIdPrefix:          secretIdPrefix,
	}
}

func getEnvVariable(name string) string {
	value := os.Getenv(name)
	if value == "" {
		log.Fatalf("environment variable %s must be set", name)
	}

	return value
}
