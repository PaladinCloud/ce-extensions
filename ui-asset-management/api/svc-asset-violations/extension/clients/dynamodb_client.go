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
	"svc-asset-violations-layer/models"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/credentials/stscreds"
	"github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/sts"
)

type DynamodbClient struct {
	region                        string
	tenantConfigTable             string
	tenantConfigTablePartitionKey string
	client                        *dynamodb.Client
}

// NewDynamoDBClient inits a DynamoDB session to be used throughout the services
func NewDynamoDBClient(configuration *Configuration) (*DynamodbClient, error) {

	// Load the default AWS configuration
	cfg, err := config.LoadDefaultConfig(context.TODO(), config.WithRegion(configuration.Region))
	if err != nil {
		return nil, fmt.Errorf("error loading AWS config: %v", err)
	}

	// Create an STS client
	stsClient := sts.NewFromConfig(cfg)

	// Assume the role using STS
	creds := stscreds.NewAssumeRoleProvider(stsClient, configuration.AssumeRoleArn, func(o *stscreds.AssumeRoleOptions) {
		o.RoleSessionName = "DynamoDBSession"
	})

	// Create a new AWS configuration with the assumed role credentials
	assumedCfg := aws.Config{
		Credentials: aws.NewCredentialsCache(creds),
		Region:      configuration.Region,
	}

	// Initialize the DynamoDB client with the assumed role credentials
	svc := dynamodb.NewFromConfig(assumedCfg)

	if err != nil {
		fmt.Errorf("error creating dynamoDB client %v", err)
	}

	fmt.Println("Initialized DynamoDB Client with assumed role")
	return &DynamodbClient{
		region:                        configuration.Region,
		client:                        svc,
		tenantConfigTable:             configuration.TenantConfigTable,
		tenantConfigTablePartitionKey: configuration.TenantConfigTablePartitionKey,
	}, nil
}

func (d *DynamodbClient) GetOpenSearchDomain(ctx context.Context, tenantId string) (*models.OpenSearchDomainProperties, error) {

	fmt.Printf("Fetching tenant configs for tenantId: %s\n", tenantId)

	key := struct {
		TenantId string `dynamodbav:"tenant_id" json:"tenant_id"`
	}{TenantId: tenantId}
	avs, err := attributevalue.MarshalMap(key)

	if err != nil {
		return nil, fmt.Errorf("failed to get item from DynamoDB: %v", err)
	}

	// Prepare the GetItemInput with the correct table name and key
	input := &dynamodb.GetItemInput{
		TableName:            aws.String(d.tenantConfigTable),     // DynamoDB table name
		Key:                  avs,                                 // Key to fetch the item
		ProjectionExpression: aws.String("datastore_es_ESDomain"), // Only fetch the required attribute
	}

	fmt.Printf("DynamoDB Client: %+v\n", d.client)
	fmt.Printf("DynamoDB input: %+v\n", input)

	// Query DynamoDB to get the item
	result, err := d.client.GetItem(ctx, input)

	if err != nil {
		return nil, fmt.Errorf("failed to get item from DynamoDB: %v", err)
	}

	// Check if the item exists
	if result.Item == nil {
		return nil, fmt.Errorf("tenant_id %s not found", tenantId)
	}

	// Unmarshal the datastore_es_ESDomain field into the struct
	var config models.TenantOutput
	if err := attributevalue.UnmarshalMap(result.Item, &config); err != nil {
		return nil, fmt.Errorf("failed to unmarshal OpenSearchDomain: %v", err)
	}

	fmt.Printf("esDomain endpoint fetched from tenant config: %s\n", config.EsDomain.Endpoint)
	return &config.EsDomain, nil
}
