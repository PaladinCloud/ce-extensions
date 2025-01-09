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

package main

import (
	"svc-core-proxy-layer/clients"
	"testing"
)

func TestStartMain(t *testing.T) {

	t.Run("main", func(t *testing.T) {
		config := &clients.Configuration{
			EnableExtension:         false,
			UseAssumeRole:           false,
			AssumeRoleArn:           "",
			Region:                  "us-east-1",
			TenantConfigTable:       "tenant-config",
			TenantConfigOutputTable: "tenant-output",
			TenantTablePartitionKey: "tenant_id",
			SecretPrefixString:      "paladincloud/secret/",
		}

		err := startMain(config)
		if err != nil {
			t.Fatalf("failed to start main %+v", err)
		}
	})
}
