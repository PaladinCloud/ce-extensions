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
	"slices"
	"strings"
	"svc-asset-state-count-layer/models"
)

type AssetCountClient struct {
	elasticSearchClient *ElasticSearchClient
	rdsClient           *RdsClient
}

func NewAssetCountClient(ctx context.Context, config *Configuration) (*AssetCountClient, error) {
	dynamodbClient, err := NewDynamoDBClient(ctx, config.UseAssumeRole, config.AssumeRoleArn, config.Region, config.TenantConfigOutputTable, config.TenantTablePartitionKey)
	if err != nil {
		return nil, fmt.Errorf("error creating dynamodb client %w", err)
	}

	secretsClient, err := NewSecretsClient(ctx, config.UseAssumeRole, config.AssumeRoleArn, config.Region)
	if err != nil {
		return nil, fmt.Errorf("error creating secrets client %w", err)
	}

	opensearchClient := NewElasticSearchClient(dynamodbClient)
	rdsClient, err := NewRdsClient(secretsClient, config.SecretIdPrefix)
	if err != nil {
		return nil, fmt.Errorf("error creating rds client %w", err)
	}

	return &AssetCountClient{
		elasticSearchClient: opensearchClient,
		rdsClient:           rdsClient,
	}, nil
}

const (
	success = "success"
)

func (c *AssetCountClient) GetAssetCountForAssetGroup(ctx context.Context, tenantId, ag, domain string) (*models.AssetStateCountResponse, error) {
	if len(strings.TrimSpace(ag)) == 0 {
		return nil, fmt.Errorf("asset group must be present")
	}

	var targetTypesFromDB *[]models.TargetTableProjection
	var err error

	plugins, err := c.rdsClient.GetCloudProviders(ctx, tenantId)
	if err != nil {
		return nil, fmt.Errorf("error fetching cloud providers %w", err)
	}

	cloudProviders := make([]string, 0, len(*plugins))
	for _, plugin := range *plugins {
		cloudProviders = append(cloudProviders, plugin.Provider)
	}

	if slices.Contains(cloudProviders, ag) {
		targetTypesFromDB, err = c.rdsClient.GetAllTargetTypes(ctx, tenantId, ag)
		if err != nil {
			return nil, fmt.Errorf("error fetching target types for cloud provider %w", err)
		}
	} else if ag == "ds-all" || ag == "*" {
		targetTypesFromDB, err = c.rdsClient.GetAllTargetTypes(ctx, tenantId, "")
		if err != nil {
			return nil, fmt.Errorf("error fetching all target types %w", err)
		}
	} else {
		// fetch asset types for the asset group from OS
		targetTypesFromDB, err = c.fetchAssetTypesForAssetGroup(ctx, tenantId, ag, domain)
		if err != nil {
			return nil, fmt.Errorf("error fetching target types for asset group %w", err)
		}
	}

	if targetTypesFromDB == nil || len(*targetTypesFromDB) == 0 {
		return nil, fmt.Errorf("no valid target types found for this asset group [%s]", ag)
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

	assetStateCounts, err := c.processAssetStateCountBuckets(assetStateCountBuckets)
	if err != nil {
		return nil, fmt.Errorf("error processing asset state count buckets %w", err)
	}
	return &models.AssetStateCountResponse{
		Data: models.AssetStateCountData{
			AssetStateNameCounts: *assetStateCounts},
		Message: success,
	}, nil
}

func (c *AssetCountClient) fetchAssetTypesForAssetGroup(ctx context.Context, tenantId, ag, domain string) (*[]models.TargetTableProjection, error) {
	agAssetTypeResponse, err := c.elasticSearchClient.FetchAssetTypesForAssetGroup(ctx, tenantId, ag)
	if err != nil {
		return nil, err
	}
	if agAssetTypeResponse == nil || len(*agAssetTypeResponse) == 0 {
		return nil, fmt.Errorf("no asset types for this asset group [%s]", ag)
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
	return c.rdsClient.GetConfiguredTargetTypes(ctx, targetTypesForAg, domain, "", tenantId)
}

func (c *AssetCountClient) processAssetStateCountBuckets(assetStateCountBuckets *[]interface{}) (*[]models.AssetStateCount, error) {
	if assetStateCountBuckets != nil && len(*assetStateCountBuckets) > 0 {
		assetStateCounts := make([]models.AssetStateCount, 0, len(*assetStateCountBuckets))
		for _, assetStateCountBucket := range *assetStateCountBuckets {
			bucket := assetStateCountBucket.(map[string]interface{})
			assetState := bucket["key"].(string)
			docCount, ok := bucket["doc_count"].(float64)
			var parsedCount int
			if !ok {
				parsedCount = 0
			} else {
				parsedCount = int(docCount)
			}
			assetStateCounts = append(assetStateCounts, models.AssetStateCount{StateName: assetState, Count: parsedCount})
		}
		return &assetStateCounts, nil
	} else {
		return &[]models.AssetStateCount{}, nil
	}
}
