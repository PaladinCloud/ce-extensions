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
	"github.com/aws/aws-sdk-go-v2/credentials/stscreds"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
	"github.com/aws/aws-sdk-go-v2/service/sts"
	"svc-asset-details-layer/models"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
)

type DynamodbClient struct {
	region                        string
	tenantConfigTable             string
	tenantConfigTablePartitionKey string
	client                        *dynamodb.Client
}

// NewDynamoDBClient inits a DynamoDB client to be used throughout the services
func NewDynamoDBClient(assumeRoleArn, region, tenantConfigTable, tenantConfigTablePartitionKey string) (*DynamodbClient, error) {
	// Load default AWS config
	cfg, err := config.LoadDefaultConfig(context.TODO(), config.WithRegion(region))
	if err != nil {
		fmt.Printf("error loading AWS config: %v", err)
		return nil, err
	}

	// Create an STS client
	stsClient := sts.NewFromConfig(cfg)

	// Assume the role using STS
	creds := stscreds.NewAssumeRoleProvider(stsClient, assumeRoleArn, func(o *stscreds.AssumeRoleOptions) {
		o.RoleSessionName = "DynamoDBSession"
	})

	// Create a new configuration with the assumed role credentials
	assumedCfg := aws.Config{
		Credentials: aws.NewCredentialsCache(creds),
		Region:      region,
	}

	// Initialize DynamoDB client
	svc := dynamodb.NewFromConfig(assumedCfg)

	fmt.Println("Initialized DynamoDB Client")
	return &DynamodbClient{
		region:                        region,
		tenantConfigTable:             tenantConfigTable,
		tenantConfigTablePartitionKey: tenantConfigTablePartitionKey,
		client:                        svc,
	}, nil
}

func (d *DynamodbClient) GetOpenSearchDomain(ctx context.Context, tenant string) (*models.OpenSearchDomainProperties, error) {
	tenantId := tenant

	fmt.Printf("Fetching tenant configs for tenantId: %s\n", tenantId)

	// Define the query input
	input := &dynamodb.QueryInput{
		TableName:              aws.String(d.tenantConfigTable),
		KeyConditionExpression: aws.String(fmt.Sprintf("%s = :tenantId", d.tenantConfigTablePartitionKey)),
		ExpressionAttributeValues: map[string]types.AttributeValue{
			":tenantId": &types.AttributeValueMemberS{Value: tenantId},
		},
		ProjectionExpression: aws.String("datastore_es_ESDomain"),
	}

	// Retrieve the item from DynamoDB
	result, err := d.client.Query(ctx, input)
	if err != nil {
		return &models.OpenSearchDomainProperties{}, fmt.Errorf("failed to get item from DynamoDB: %v", err)
	}

	// Check if the item is found
	if len(result.Items) == 0 {
		return &models.OpenSearchDomainProperties{}, fmt.Errorf("tenant_id %s not found", tenantId)
	}

	// Unmarshal the result into TenantConfig struct
	var config models.TenantConfig
	err = attributevalue.UnmarshalMap(result.Items[0], &config)
	if err != nil {
		return &models.OpenSearchDomainProperties{}, fmt.Errorf("failed to unmarshal result: %v", err)
	}
	fmt.Printf("esDomain endpoint fetched from tenant config: %s\n", config.EsDomain.Endpoint)

	return &config.EsDomain, nil
}
