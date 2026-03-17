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
	dynamodbClient, err := NewDynamoDBClient(ctx, config.UseAssumeRole, config.AssumeRoleArn, config.Region, config.TenantConfigTable, config.TenantConfigOutputTable, config.TenantTablePartitionKey)
	if err != nil {
		return nil, fmt.Errorf("error creating dynamodb client %w", err)
	}

	secretsClient, err := NewSecretsClient(ctx, config.UseAssumeRole, config.AssumeRoleArn, config.Region, config.SecretPrefixString)
	if err != nil {
		return nil, fmt.Errorf("error creating secrets client %w", err)
	}

	rdsClient, err := NewRdsClient(secretsClient)
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

func (c *ProxyClient) GetTenantSecretDetails(ctx context.Context, tenantId, secretName string) (*models.Response, error) {
	secret, err := c.secretsClient.GetTenantSecretData(ctx, tenantId, secretName)
	if err != nil {
		return nil, fmt.Errorf("failed to get secret %w", err)
	}

	if secret == nil {
		return nil, fmt.Errorf("secret is missing")
	}

	return models.ConvertSecretToResponse(secret), nil
}

func (c *ProxyClient) GetTenantFeatures(ctx context.Context, tenantId string) (*models.Response, error) {
	tenantFeatures, err := c.dynamodbClient.GetTenantFeatureFlags(ctx, tenantId)
	if err != nil {
		return nil, fmt.Errorf("failed to get tenant feature flags %w", err)
	}

	if tenantFeatures == nil {
		return nil, fmt.Errorf("tenant feature flags are missing")
	}

	return models.ConvertTenantFeatureFlagsToResponse(*tenantFeatures), nil
}

func (c *ProxyClient) GetTenantRdsDetails(ctx context.Context, tenantId string) (*models.Response, error) {
	rdsCredentials, err := c.secretsClient.GetTenantRdsSecret(ctx, tenantId)
	if err != nil {
		return nil, fmt.Errorf("failed to get rds credentials %w", err)
	}

	if rdsCredentials == nil {
		return nil, fmt.Errorf("rds credentials are missing")
	}

	return models.ConvertRdsSecretToResponse(rdsCredentials), nil
}

func (c *ProxyClient) GetTenantOpenSearchDetails(ctx context.Context, tenantId string) (*models.Response, error) {
	osProperties, err := c.dynamodbClient.GetOpenSearchDomain(ctx, tenantId)
	if err != nil {
		return nil, fmt.Errorf("failed to get OpenSearch domain: %w", err)
	}

	return models.ConvertOsPropertiesToResponse(osProperties), nil
}

func (c *ProxyClient) GetTenantOutputDetails(ctx context.Context, tenantId string, key string) (*models.Response, error) {
	props, err := c.dynamodbClient.GetTenantOutput(ctx, tenantId, key)
	if err != nil {
		return nil, fmt.Errorf("failed to get tenant output: %w", err)
	}

	return models.ConvertOutputResponse(props), nil
}

func (c *ProxyClient) GetSecretDetails(ctx context.Context, secretName string) (*models.Response, error) {
	props, err := c.secretsClient.GetSecretData(ctx, secretName)
	if err != nil {
		return nil, fmt.Errorf("failed to get secret: %w", err)
	}
	return models.ConvertOutputResponse(props), nil
}
