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
	"cache-layer/src/ext/models"
	"context"
	"fmt"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"log"
)

type DynamodbClient struct {
	configuration *Configuration
	client        *dynamodb.DynamoDB
}

// NewDynamoDBClient inits a DynamoDB session to be used throughout the services
func NewDynamoDBClient(configuration *Configuration) *DynamodbClient {
	sess, err := session.NewSession(&aws.Config{
		Region: aws.String(configuration.Region),
	})

	if err != nil {
		log.Fatal(err)
	}

	svc := dynamodb.New(sess)

	return &DynamodbClient{
		configuration: configuration,
		client:        svc,
	}
}

func (d *DynamodbClient) GetPluginFeatureFlags(ctx context.Context) (*models.PluginFeatureFlags, error) {
	tenantId := d.configuration.TenantId

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
		return &models.PluginFeatureFlags{}, fmt.Errorf("failed to get item from DynamoDB: %v", err)
	}

	// Check if the item is found
	if len(result.Items) == 0 {
		return &models.PluginFeatureFlags{}, fmt.Errorf("tenant_id %s not found", tenantId)
	}

	// Unmarshal the result into TenantConfig struct
	var config models.TenantConfig
	err = dynamodbattribute.UnmarshalMap(result.Items[0], &config)
	if err != nil {
		return &models.PluginFeatureFlags{}, fmt.Errorf("failed to unmarshal result: %v", err)
	}

	return &config.PluginFeatureFlags, nil
}
