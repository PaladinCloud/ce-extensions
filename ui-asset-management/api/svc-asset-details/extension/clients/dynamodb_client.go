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
	"log"
	"svc-asset-details-layer/models"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/credentials/stscreds"
	"github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/sts"
	"github.com/google/uuid"
)

type DynamodbClient struct {
	region                  string
	tenantConfigOutputTable string
	tenantTablePartitionKey string
	client                  *dynamodb.Client
}

// NewDynamoDBClient inits a DynamoDB session to be used throughout the services
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

	fmt.Println("initialized dynamodb client")
	return &DynamodbClient{
		region:                  region,
		client:                  svc,
		tenantConfigOutputTable: tenantConfigOutputTable,
		tenantTablePartitionKey: tenantTablePartitionKey,
	}, nil
}

func (d *DynamodbClient) GetOpenSearchDomain(ctx context.Context, tenantId string) (*models.OpenSearchDomainProperties, error) {
	log.Printf("fetching tenant configs for tenant id [%s]\n", tenantId)
	const projectionExpression = "datastore_es_ESDomain"

	key := struct {
		TenantId string `dynamodbav:"tenant_id" json:"tenant_id"`
	}{TenantId: tenantId}
	avs, err := attributevalue.MarshalMap(key)

	if err != nil {
		return nil, fmt.Errorf("failed to construct dynamodb key %w", err)
	}

	// Prepare the GetItemInput with the correct table name and key
	input := &dynamodb.GetItemInput{
		TableName:            aws.String(d.tenantConfigOutputTable), // DynamoDB table name
		Key:                  avs,                                   // Key to fetch the item
		ProjectionExpression: aws.String(projectionExpression),      // Only fetch the required attribute
	}

	// Query DynamoDB to get the item
	result, err := d.client.GetItem(ctx, input)
	if err != nil {
		return nil, fmt.Errorf("failed to get opensearch client domain from dynamoddb for tenant id [%s] %w", tenantId, err)
	}

	// Check if the item exists
	if result.Item == nil {
		return nil, fmt.Errorf("tenant id [%s] not found in dynamodb [%s] table", tenantId, d.tenantConfigOutputTable)
	}

	var output models.TenantOutput
	if err2 := attributevalue.UnmarshalMap(result.Item, &output); err2 != nil {
		return nil, fmt.Errorf("failed to unmarshal [%s] %w", projectionExpression, err2)
	}

	log.Printf("fetched [%s] endpoint from [%s]\n", output.EsDomain.Endpoint, projectionExpression)
	return &output.EsDomain, nil
}
