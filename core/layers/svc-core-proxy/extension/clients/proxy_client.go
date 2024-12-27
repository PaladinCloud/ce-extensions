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
	"fmt"
	"svc-core-proxy-layer/models"
)

type ProxyClient struct {
	configuration  *Configuration
	secretsClient  *SecretsClient
	dynamodbClient *DynamodbClient
	rdsClient      *RdsClient
}

func NewProxyClient(ctx context.Context, config *Configuration) (*ProxyClient, error) {
	dynamodbClient, err := NewDynamoDBClient(ctx, config.UseAssumeRole, config.AssumeRoleArn, config.Region, config.TenantConfigOutputTable, config.TenantTablePartitionKey)
	if err != nil {
		return nil, fmt.Errorf("error creating dynamodb client %w", err)
	}

	secretsClient, err := NewSecretsClient(ctx, config.UseAssumeRole, config.AssumeRoleArn, config.Region)
	if err != nil {
		return nil, fmt.Errorf("error creating secrets client %w", err)
	}

	rdsClient, err := NewRdsClient(secretsClient, config.SecretIdPrefix)
	if err != nil {
		return nil, fmt.Errorf("error creating rds client %w", err)
	}

	return &ProxyClient{
		configuration:  config,
		secretsClient:  secretsClient,
		dynamodbClient: dynamodbClient,
		rdsClient:      rdsClient,
	}, nil
}

// TODO: migrate to asset model v2 when available
const (
	allSources = "all-sources"
	success    = "success"
	empty      = "<missing>"
)

const (
	osUrl = "osUrl"
)

func (c *ProxyClient) GetTenantFeatures(ctx context.Context, tenantId string) (*models.Response, error) {

	return nil, nil
}

func (c *ProxyClient) GetTenantRdsDetails(ctx context.Context, tenantId string) (*models.Response, error) {
	rdsCredentials, err := c.secretsClient.GetRdsSecret(ctx, c.configuration.SecretIdPrefix, tenantId)
	if err != nil {
		return nil, fmt.Errorf("failed to get rds credentials %w", err)
	}

	if rdsCredentials == nil {
		return nil, fmt.Errorf("rds credentials are missing")
	}

	return ConvertRdsSecretToResponse(rdsCredentials), nil
}

func (c *ProxyClient) GetTenantOpenSearchDetails(ctx context.Context, tenantId string) (*models.Response, error) {
	osProperties, err := c.dynamodbClient.GetOpenSearchDomain(ctx, tenantId)
	if err != nil {
		return nil, fmt.Errorf("failed to get OpenSearch domain: %w", err)
	}

	return ConvertOsPropertiesToResponse(osProperties), nil
}

func ConvertRdsSecretToResponse(secret *models.RdsSecret) *models.Response {
	return &models.Response{
		Data: map[string]string{
			"DB_USERNAME": secret.DbUsername,
			"DB_PASSWORD": secret.DbPassword,
			"DB_NAME":     secret.DbName,
			"RDS_HOST":    secret.DbHost,
			"RDS_PORT":    secret.DbPort,
		},
	}
}

func ConvertOsPropertiesToResponse(osProperties *models.OpenSearchProperties) *models.Response {
	return &models.Response{
		Data: map[string]string{
			osUrl: osProperties.EsDomain.Endpoint,
		},
	}
}
