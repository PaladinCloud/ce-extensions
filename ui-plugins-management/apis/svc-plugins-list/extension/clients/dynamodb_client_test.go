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
	"testing"
)

// Test the dynamodb client
func TestDynamodbClient(t *testing.T) {
	ctx := context.Background()
	c := &Configuration{
		EnableExtension:         false,
		UseAssumeRole:           false,
		Region:                  "us-east-1",
		TenantConfigTable:       "tenant-config",
		TenantConfigOutputTable: "tenant-output",
		TenantTablePartitionKey: "tenant_id",
	}

	client, err := NewDynamoDBClient(ctx, c.UseAssumeRole, c.AssumeRoleArn, c.Region, c.TenantConfigTable, c.TenantConfigOutputTable, c.TenantTablePartitionKey)
	if err != nil {
		t.Fatalf("Failed to create DynamoDB client: %+v", err)
	}

	pluginFeatureFlags, err := client.GetPluginsFeatureFlags(ctx, "tenant_id")
	if err != nil {
		panic(err)
	}

	println(pluginFeatureFlags)
}
