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
	"regexp"
	"strconv"
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
	enableExtension, err := parseEnableExtension()
	if err != nil {
		return nil, err
	}
	useAssumeRole, assumeRoleArn, err := parseAssumeRole()
	if err != nil {
		return nil, err
	}

	// Load the region and other configuration details and fail if not set
	region, err := getEnvVariable("REGION")
	if err != nil {
		return nil, err
	}
	tenantConfigOutputTable, err := getEnvVariable("TENANT_CONFIG_OUTPUT_TABLE")
	if err != nil {
		return nil, err
	}
	tenantTablePartitionKey, err := getEnvVariable("TENANT_TABLE_PARTITION_KEY")
	if err != nil {
		return nil, err
	}
	secretIdPrefix, err := getEnvVariable("SECRET_NAME_PREFIX")
	if err != nil {
		return nil, err
	}

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

func getEnvVariable(name string) (string, error) {
	value := os.Getenv(name)
	if value == "" {
		return "", fmt.Errorf("required environment variable [%s] is not set", name)
	}

	return value, nil
}

func parseEnableExtension() (bool, error) {
	if val := os.Getenv("ENABLE_EXTENSION"); val != "" {
		parsedVal, err := strconv.ParseBool(val)
		if err != nil {
			return false, fmt.Errorf("invalid value for ENABLE_EXTENSION [%s]", val)
		}

		return parsedVal, nil
	}

	return true, nil
}

func parseAssumeRole() (bool, string, error) {
	arn := os.Getenv("ASSUME_ROLE_ARN")
	// We only want to use assume role if dynamodb config table and the secrets manager are in a different account
	if arn == "" {
		return false, "", nil
	}

	// Validate complete IAM role ARN format
	arnPattern := `^arn:aws:iam::\d{12}:role/[\w+=,.@-]+$`
	if matched, _ := regexp.MatchString(arnPattern, arn); !matched {
		return false, "", fmt.Errorf("invalid value for ASSUME_ROLE_ARN [%s]", arn)
	}

	return true, arn, nil
}
