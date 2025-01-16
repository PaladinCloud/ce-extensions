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

func TestNewRDSClient(t *testing.T) {
	ctx := context.Background()

	tenantId := ""
	useAssumeRole := false
	assumeRoleArn := ""
	region := "us-east-1"
	SecretPrefixString := "paladincloud/secret/"

	secretsClient, _ := NewSecretsClient(ctx, useAssumeRole, assumeRoleArn, region, SecretPrefixString)

	client, err := NewRdsClient(secretsClient)
	if err != nil {
		t.Fatalf("Failed to create RDS client: %v", err)
	}
	response, err := client.FetchMandatoryTags(ctx, tenantId)

	if err != nil {
		t.Errorf("Error while fetching RDS Instance: %+v", err)
	}

	if response == nil {
		t.Errorf("Expected response to be not nil")
	}

	t.Logf("Successfully fetched RDS Instance %+v", response)
}
