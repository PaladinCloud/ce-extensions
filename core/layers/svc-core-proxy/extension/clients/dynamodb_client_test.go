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

func TestGetOpenSearchDomain(t *testing.T) {
	ctx := context.Background()

	useAssumeRole := false
	assumeRole := "arn:aws:iam::{accountID}:role/{roleName}"

	region := "us-east-1"

	tenantId := "98c28482-9bae-46bd-bd4e-58fa132e72c0"

	tenantConfigOutputTable := "tenant-output"
	tenantConfigTablePartitionKey := "tenant_id"

	client, err := NewDynamoDBClient(ctx, useAssumeRole, assumeRole, region, tenantConfigOutputTable, tenantConfigTablePartitionKey)
	if err != nil {
		t.Fatalf("Failed to create DynamoDB client: %+v", err)
	}

	response, err := client.GetOpenSearchDomain(ctx, tenantId)
	if err != nil {
		t.Fatalf("Error while fetching OpenSearch Domain: %+v", err)
	}

	if response == nil {
		t.Fatal("Expected response to be not nil")
	}

	if response.EsDomain.Endpoint == "" {
		t.Fatal("Expected response.EsDomain.Endpoint to be not nil")
	}

	t.Logf("Successfully fetched OpenSearch Domain %+v", response)
}
