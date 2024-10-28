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
	"strconv"
	"strings"
	"svc-asset-count-layer/models"
)

type AssetCountClient struct {
	elasticSearchClient *ElasticSearchClient
	rdsClient           *RdsClient
}

func NewAssetCountClient(ctx context.Context, config *Configuration) *AssetCountClient {
	dynamodbClient, _ := NewDynamoDBClient(ctx, config.UseAssumeRole, config.AssumeRoleArn, config.Region, config.TenantConfigOutputTable, config.TenantTablePartitionKey)
	secretsClient, _ := NewSecretsClient(ctx, config.UseAssumeRole, config.AssumeRoleArn, config.Region)

	return &AssetCountClient{
		elasticSearchClient: NewElasticSearchClient(dynamodbClient),
		rdsClient:           NewRdsClient(secretsClient, config.SecretIdPrefix),
	}
}

const (
	success     = "success"
	awsProvider = "aws"
	gcp         = "gcp"
	azure       = "azure"
)

func (c *AssetCountClient) GetAssetCountForAssetGroup(ctx context.Context, tenantId, ag, domain string) (*models.AssetStateCountResponse, error) {
	if len(strings.TrimSpace(ag)) == 0 {
		return nil, fmt.Errorf("asset group must be present")
	}

	var targetTypesFromDB *[]models.TargetTableProjection
	var err error
	if ag == awsProvider || ag == gcp || ag == azure {
		targetTypesFromDB, err = c.rdsClient.GetAllTargetTypes(ctx, tenantId, ag)

	} else if ag == "ds-all" || ag == "*" {
		targetTypesFromDB, err = c.rdsClient.GetAllTargetTypes(ctx, tenantId, "")
	} else {
		// fetch asset types for the asset group from OS
		agAssetTypeResponse, err := c.elasticSearchClient.FetchAssetTypesForAssetGroup(ctx, tenantId, ag)
		if err != nil {
			return nil, err
		}
		if agAssetTypeResponse == nil || len(*agAssetTypeResponse) == 0 {
			return nil, fmt.Errorf("no asset types for this asset group: %s", ag)
		}
		targetTypesForAg := make([]string, 0, len(*agAssetTypeResponse))
		for k, _ := range *agAssetTypeResponse {
			underscoreIndex := strings.Index(k, "_")
			if underscoreIndex != -1 && underscoreIndex < len(k)-1 {
				targetTypesForAg = append(targetTypesForAg, k[underscoreIndex+1:])
			} else {
				targetTypesForAg = append(targetTypesForAg, k)
			}
		}
		// fetch only configured asset types from target and accounts table
		targetTypesFromDB, err = c.rdsClient.GetConfiguredTargetTypes(ctx, targetTypesForAg, domain, "", tenantId)
	}
	if err != nil {
		return nil, err
	}
	if targetTypesFromDB == nil || len(*targetTypesFromDB) == 0 {
		return nil, fmt.Errorf("no valid target types found for this asset group: %s", ag)
	}
	validTargetTypes := make([]string, 0, len(*targetTypesFromDB))
	for _, targetType := range *targetTypesFromDB {
		validTargetTypes = append(validTargetTypes, targetType.Type)
	}

	// fetch violations for the asset
	assetStateCountBuckets, err := c.elasticSearchClient.FetchAssetStateCount(ctx, tenantId, ag, validTargetTypes)
	if err != nil {
		return nil, err
	}

	if assetStateCountBuckets != nil && len(*assetStateCountBuckets) > 0 {
		assetStateCounts := make([]models.AssetStateCount, 0, len(*assetStateCountBuckets))
		for _, assetStateCountBucket := range *assetStateCountBuckets {
			bucket := assetStateCountBucket.(map[string]interface{})
			assetState := bucket["key"].(string)
			countString := fmt.Sprintf("%v", bucket["doc_count"])
			parsedCount, err := strconv.Atoi(countString)
			if err != nil {
				parsedCount = 0
			}
			assetStateCounts = append(assetStateCounts, models.AssetStateCount{StateName: assetState, Count: parsedCount})
		}
		return &models.AssetStateCountResponse{Data: models.AssetStateCountData{AssetStateNameCounts: assetStateCounts}, Message: success}, nil
	} else {
		return &models.AssetStateCountResponse{Data: models.AssetStateCountData{AssetStateNameCounts: []models.AssetStateCount{}}, Message: success}, nil
	}
}
