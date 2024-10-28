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
	"log"
	"os"
	"strconv"
	"strings"
)

type Configuration struct {
	EnableExtension         bool
	UseAssumeRole           bool
	AssumeRoleArn           string
	Region                  string
	TenantConfigOutputTable string
	TenantTablePartitionKey string
	SecretIdPrefix          string
}

func LoadConfigurationDetails() (*Configuration, error) {
	enableExtension := parseEnableExtension()
	useAssumeRole, assumeRoleArn := parseAssumeRole()

	// Load the region and other configuration details and fail if not set
	region := getEnvVariable("REGION")
	tenantConfigOutputTable := getEnvVariable("TENANT_CONFIG_OUTPUT_TABLE")
	tenantTablePartitionKey := getEnvVariable("TENANT_TABLE_PARTITION_KEY")
	secretIdPrefix := getEnvVariable("SECRET_NAME_PREFIX")

	return &Configuration{
		EnableExtension:         enableExtension,
		UseAssumeRole:           useAssumeRole,
		AssumeRoleArn:           assumeRoleArn,
		Region:                  region,
		TenantConfigOutputTable: tenantConfigOutputTable,
		TenantTablePartitionKey: tenantTablePartitionKey,
		SecretIdPrefix:          secretIdPrefix,
	}, nil
}

func getEnvVariable(name string) string {
	value := os.Getenv(name)
	if value == "" {
		log.Fatalf("required environment variable [%s] is not set", name)
	}

	return value
}

func parseEnableExtension() bool {
	if val := os.Getenv("ENABLE_EXTENSION"); val != "" {
		parsedVal, err := strconv.ParseBool(val)
		if err != nil {
			log.Fatalf("invalid value for ENABLE_EXTENSION [%s]", val)
		}

		return parsedVal
	}

	return true
}

func parseAssumeRole() (bool, string) {
	arn := os.Getenv("ASSUME_ROLE_ARN")
	// We only want to use assume role if dynamodb config table and the secrets manager are in a different account
	if arn == "" {
		return false, ""
	}

	// Validate ARN format
	if !strings.HasPrefix(arn, "arn:aws:iam::") {
		log.Fatalf("invalid role ARN format [%s]", arn)
		return false, ""
	}

	return true, arn
}
