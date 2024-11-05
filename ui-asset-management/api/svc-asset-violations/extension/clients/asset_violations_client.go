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
	"math"
	"strconv"
	"strings"
	"svc-asset-violations-layer/models"
)

type AssetViolationsClient struct {
	elasticSearchClient *ElasticSearchClient
	rdsClient           *RdsClient
}

func NewAssetViolationsClient(ctx context.Context, config *Configuration) (*AssetViolationsClient, error) {
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

	return &AssetViolationsClient{
		elasticSearchClient: opensearchClient,
		rdsClient:           rdsClient,
	}, nil
}

const (
	allSources            = "all-sources"
	success               = "success"
	noActivePolicyMessage = "No active policies monitoring this asset type"
	noPolicyMessage       = "There are no policies for this asset type"
)

var severityWeights = map[string]int{"critical": 10, "high": 5, "medium": 3, "low": 1}

func (c *AssetViolationsClient) GetAssetViolations(ctx context.Context, targetType, tenantId, assetId string) (*models.AssociatedPoliciesResponse, error) {
	if len(strings.TrimSpace(assetId)) == 0 {
		return nil, fmt.Errorf("assetId must be present")
	}
	if len(strings.TrimSpace(targetType)) == 0 {
		return nil, fmt.Errorf("targetType must be present")
	}

	// fetch all policies(ENABLED/DISABLED) count for the target type
	policyCount, err := c.rdsClient.GetAllPoliciesCount(ctx, tenantId, targetType)
	if policyCount == 0 {
		log.Printf("no policies for given target type [%s]\n", targetType)
		return &models.AssociatedPoliciesResponse{Data: models.AssociatedPolicies{}, Message: noPolicyMessage}, nil
	}

	// fetch all the relevant policies for the target type
	policies, err := c.rdsClient.GetEnabledPolicies(ctx, tenantId, targetType)
	if err != nil {
		return nil, fmt.Errorf("error fetching policies from rds for target type [%s] %w", targetType, err)
	}

	if policies == nil || len(policies) == 0 {
		log.Printf("no enabled policies for given target type [%s]\n", targetType)
		return &models.AssociatedPoliciesResponse{Data: models.AssociatedPolicies{}, Message: noActivePolicyMessage}, nil
	}

	log.Printf("got [%s] policies for target type [%s]\n", strconv.Itoa(len(policies)), targetType)

	// fetch violations for the asset
	policyIds := extractPolicyIds(policies)
	violations, err := c.elasticSearchClient.FetchAssetViolationsWithAggregations(ctx, tenantId, allSources, assetId, policyIds)
	if err != nil {
		return nil, fmt.Errorf("error fetching asset violations from elasticsearch for asset id [%s] %w", assetId, err)
	}

	associatedPolicies := assembleAssociatedPoliciesResponse(policies, *violations)

	return &models.AssociatedPoliciesResponse{Data: *associatedPolicies, Message: success}, nil
}

func buildSeverityInfo(severityCounts []models.Bucket) map[string]int {
	severityInfoMap := make(map[string]int)
	for _, bucket := range severityCounts {
		severityInfoMap[bucket.Key] = bucket.DocCount
	}

	return severityInfoMap
}

func extractPolicyIds(policies []models.PolicyRdsResult) []string {
	var policyIds []string
	for _, policy := range policies {
		policyIds = append(policyIds, policy.PolicyId)
	}

	return policyIds
}

func createPoliciesWithIssueIds(dbPolicies []models.PolicyRdsResult, esResult *models.ViolationsOpenSearchResult) ([]models.Policy, int) {
	policies := make([]models.Policy, 0, len(dbPolicies))
	issueByPolicyMap := make(map[string]models.InnerHits)

	// Extract issue IDs from ElasticSearchResult
	for _, hit := range esResult.Hits.Hits {
		issueByPolicyMap[hit.Source.PolicyID] = hit
	}

	// Create Policy object
	var totalPoliciesWeights int
	for _, dbPolicy := range dbPolicies {
		totalPoliciesWeights += severityWeights[dbPolicy.Severity]

		policy := models.Policy{
			PolicyId:   dbPolicy.PolicyId,
			PolicyName: dbPolicy.PolicyName,
			Severity:   dbPolicy.Severity,
			Category:   dbPolicy.Category,
		}

		// Check if the policy has an associated issue and set the Issue ID and Last Scan Status
		hit, exists := issueByPolicyMap[dbPolicy.PolicyId]
		if exists {
			policy.LastScanStatus = hit.Source.IssueStatus // Issue status from ElasticSearch
			policy.IssueId = hit.ID                        // Issue ID from ElasticSearch
		}

		policies = append(policies, policy)
	}

	return policies, totalPoliciesWeights
}

func assembleAssociatedPoliciesResponse(dbPolicies []models.PolicyRdsResult, violationsResults models.ViolationsOpenSearchResult) *models.AssociatedPolicies {
	policies, totalSeverityWeights := createPoliciesWithIssueIds(dbPolicies, &violationsResults)

	// Calculate compliance percentage
	aggregatedSeverityMap := getAggregatedSeverityMap(violationsResults.Aggregations.Severity.Buckets)
	compliance := calculateCompliancePercent(totalSeverityWeights, aggregatedSeverityMap)
	violations := buildSeverityInfo(violationsResults.Aggregations.Severity.Buckets)

	return &models.AssociatedPolicies{
		Policies: policies,
		Violations: models.ViolationSummary{
			Violations: violations,
			Compliance: compliance,
		},
	}
}

func getAggregatedSeverityMap(severityCounts []models.Bucket) map[string]int {
	severityMap := make(map[string]int)
	for _, bucket := range severityCounts {
		severityMap[bucket.Key] = bucket.DocCount
	}

	return severityMap
}

func calculateCompliancePercent(totalPolicySeverityWeights int, severityCounts map[string]int) int {
	if totalPolicySeverityWeights > 0 {
		violatedPolicySeverityWeights :=
			severityCounts["critical"]*severityWeights["critical"] +
				severityCounts["high"]*severityWeights["high"] +
				severityCounts["medium"]*severityWeights["medium"] +
				severityCounts["low"]*severityWeights["low"]

		compliance := 100 - (violatedPolicySeverityWeights * 100 / totalPolicySeverityWeights)
		return int(math.Floor(float64(compliance)))
	} else {
		return 100
	}
}
