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
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"log"
	"svc-core-proxy-layer/models"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/credentials/stscreds"
	"github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/sts"
	"github.com/google/uuid"
)

const (
	projectionExpressionOpenSearchDomain = "datastore_es_ESDomain"
)

type DynamodbClient struct {
	region                  string
	tenantConfigOutputTable string
	tenantTablePartitionKey string
	client                  *dynamodb.Client
}

// NewDynamoDBClient inits a DynamoDB session to be used throughout the layers
func NewDynamoDBClient(ctx context.Context, useAssumeRole bool, assumeRoleArn, region, tenantConfigOutputTable, tenantTablePartitionKey string) (*DynamodbClient, error) {
	// Load the default AWS configuration
	cfg, err := config.LoadDefaultConfig(ctx, config.WithRegion(region))
	if err != nil {
		return nil, fmt.Errorf("error loading AWS config %w", err)
	}

	var svc *dynamodb.Client
	if useAssumeRole {
		// Create an STS client
		stsClient := sts.NewFromConfig(cfg)

		// Assume the role using STS
		creds := stscreds.NewAssumeRoleProvider(stsClient, assumeRoleArn, func(o *stscreds.AssumeRoleOptions) {
			o.RoleSessionName = fmt.Sprintf("DynamodDBSession-%s", uuid.New())
		})

		// Create a new AWS configuration with the assumed role credentials
		assumedCfg := aws.Config{
			Credentials: aws.NewCredentialsCache(creds),
			Region:      region,
		}

		// Initialize the DynamoDB client with the assumed role credentials
		svc = dynamodb.NewFromConfig(assumedCfg)
	} else {
		svc = dynamodb.NewFromConfig(cfg)
	}

	log.Println("initialized dynamodb client")
	return &DynamodbClient{
		region:                  region,
		client:                  svc,
		tenantConfigOutputTable: tenantConfigOutputTable,
		tenantTablePartitionKey: tenantTablePartitionKey,
	}, nil
}

func (d *DynamodbClient) GetPluginsFeatureFlags(ctx context.Context, tenant string) (*models.PluginsFeatures, error) {
	tenantId := tenant

	// Define the query input
	input := &dynamodb.QueryInput{
		TableName: aws.String(d.configuration.TenantConfigTable),
		KeyConditions: map[string]*dynamodb.Condition{
			d.configuration.TenantConfigPartitionKey: {
				ComparisonOperator: aws.String("EQ"),
				AttributeValueList: []*dynamodb.AttributeValue{
					{
						S: aws.String(tenantId),
					},
				},
			},
		},
		ProjectionExpression: aws.String("plugin_feature_flags"),
	}

	// Retrieve the item from DynamoDB
	result, err := d.client.QueryWithContext(ctx, input)
	if err != nil {
		return &models.PluginsFeatures{}, fmt.Errorf("failed to get item from DynamoDB: %v", err)
	}

	// Check if the item is found
	if len(result.Items) == 0 {
		return &models.PluginsFeatures{}, fmt.Errorf("tenant_id %s not found", tenantId)
	}

	// Unmarshal the result into TenantConfig struct
	var config models.TenantConfig
	err = dynamodbattribute.UnmarshalMap(result.Items[0], &config)
	if err != nil {
		return &models.PluginsFeatures{}, fmt.Errorf("failed to unmarshal result: %v", err)
	}

	return &config.PluginsFeatures, nil
}

func (d *DynamodbClient) GetConfigDynamodbItem(ctx context.Context, tenantId, projection string) (*models.DynamodbItems, error) {
	result, err := GetItem[models.DynamodbItems, string](
		ctx,
		*d,
		tenantId,
		d.tenantConfigOutputTable,
		projection,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to get config dynamodb item for tenant id [%s]: %w", tenantId, err)
	}

	return result, nil
}

func (d *DynamodbClient) GetOpenSearchDomain(ctx context.Context, tenantId string) (*models.OpenSearchProperties, error) {
	result, err := GetItem[models.OpenSearchProperties, string](
		ctx,
		*d,
		tenantId,
		d.tenantConfigOutputTable,
		projectionExpressionOpenSearchDomain,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to get config dynamodb item for tenant id [%s]: %w", tenantId, err)
	}

	return result, nil
}

func GetItem[T any, K comparable](ctx context.Context, d DynamodbClient, tenantId, tableName, projectionExpression string) (*T, error) {
	log.Printf("fetching item from table [%s] with tenant id [%v] and projection [%s]\n", tableName, tenantId, projectionExpression)
	key := struct {
		TenantId string `dynamodbav:"tenant_id" json:"tenant_id"`
	}{TenantId: tenantId}

	// Marshal the key into a DynamoDB attribute map
	avs, err := attributevalue.MarshalMap(key)
	if err != nil {
		return nil, fmt.Errorf("failed to construct dynamodb key: %w", err)
	}

	// Prepare the GetItem input
	input := &dynamodb.GetItemInput{
		TableName:            aws.String(tableName),
		Key:                  avs,
		ProjectionExpression: aws.String(projectionExpression),
	}

	// Execute the GetItem operation
	result, err := d.client.GetItem(ctx, input)
	if err != nil {
		return nil, fmt.Errorf("failed to get item from dynamodb table [%s]: %w", tableName, err)
	}

	// Check if the item exists
	if result.Item == nil {
		return nil, fmt.Errorf("item not found in dynamodb table [%s]", tableName)
	}

	// Unmarshal the result into the output type T
	var output T
	if err := attributevalue.UnmarshalMap(result.Item, &output); err != nil {
		return nil, fmt.Errorf("failed to unmarshal item into type [%T]: %w", output, err)
	}

	return &output, nil
}
