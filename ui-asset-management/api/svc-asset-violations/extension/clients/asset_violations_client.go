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
	allSources  = "all-sources"
	open        = "open"
	fail        = "Fail"
	exempted    = "exempted"
	exempt      = "Exempt"
	pass        = "Pass"
	issueStatus = "issueStatus"
	policyId    = "policyId"
	success     = "success"
	managed     = "Managed"
	unmanaged   = "Unmanaged"
)

var severities = [4]string{"low", "medium", "high", "critical"}
var severityWeights = map[string]int{"low": 1, "medium": 3, "high": 5, "critical": 10}

func (c *AssetViolationsClient) GetAssetViolations(ctx context.Context, targetType, tenantId, assetId string) (*models.AssetViolations, error) {
	if len(strings.TrimSpace(assetId)) == 0 {
		return nil, fmt.Errorf("assetId must be present")
	}
	if len(strings.TrimSpace(targetType)) == 0 {
		return nil, fmt.Errorf("targetType must be present")
	}

	// fetch all the relevant policies for the target type
	policies, err := c.rdsClient.GetPolicies(ctx, tenantId, targetType)
	if err != nil {
		return nil, fmt.Errorf("error fetching policies from rds for target type [%s] %w", targetType, err)
	}

	if policies == nil || len(policies) == 0 {
		log.Printf("no policies for given target type [%s]\n", targetType)
		return &models.AssetViolations{Data: models.PolicyViolations{Coverage: unmanaged}, Message: success}, nil
	}

	log.Printf("got [%s] policies for target type [%s]\n", strconv.Itoa(len(policies)), targetType)

	// fetch violations for the asset
	policyViolationMap, err := c.elasticSearchClient.FetchAssetViolations(ctx, tenantId, allSources, assetId)
	if err != nil {
		return nil, fmt.Errorf("error fetching asset violations from elasticsearch for asset id [%s] %w", assetId, err)
	}

	policyViolations := assemblePolicyViolations(policies, *policyViolationMap)

	return &models.AssetViolations{Data: *policyViolations, Message: success}, nil
}

func buildSeverityInfo(severityCounts map[string]int) []models.SeverityInfo {
	severityInfoArr := make([]models.SeverityInfo, 0, len(severities))
	for k, v := range severityCounts {
		severityInfo := models.SeverityInfo{Severity: k, Count: v}
		severityInfoArr = append(severityInfoArr, severityInfo)
	}
	return severityInfoArr
}

func calculateCompliancePercent(totalPolicySeverityWeights int, severityCounts map[string]int) int {
	violatedPolicySeverityWeights := severityCounts["critical"]*severityWeights["critical"] + severityCounts["high"]*severityWeights["high"] + severityCounts["medium"]*severityWeights["medium"] + severityCounts["low"]*severityWeights["low"]
	if totalPolicySeverityWeights > 0 {
		compliance := 100 - (violatedPolicySeverityWeights * 100 / totalPolicySeverityWeights)
		return int(math.Floor(float64(compliance)))
	} else {
		return 100
	}
}

func assemblePolicyViolations(policies []models.Policy, policyViolationsMap models.PolicyViolationsMap) *models.PolicyViolations {
	policyViolations := models.PolicyViolations{Violations: []models.Violation{}}
	severityCount := make(map[string]int, len(severities))
	for _, severity := range severities {
		severityCount[severity] = 0
	}

	var totalSeverityWeights int
	totalViolations := 0
	for _, policy := range policies {
		var lastScanStatus string
		var issueId string
		var evaluationStatus string

		if policyViolationsMap.PolicyViolationsMap[policy.PolicyId] != nil {
			violationInfo := policyViolationsMap.PolicyViolationsMap[policy.PolicyId].(map[string]interface{})
			lastScanStatus = violationInfo[issueStatus].(string)
			issueId = violationInfo["_id"].(string)
		}
		if lastScanStatus == open {
			evaluationStatus = fail
			totalViolations++
			severityCount[policy.Severity] = severityCount[policy.Severity] + 1
		} else if lastScanStatus == exempted {
			evaluationStatus = exempt
		} else {
			evaluationStatus = pass
		}

		policyViolations.Violations = append(policyViolations.Violations, models.Violation{
			PolicyId:       policy.PolicyId,
			PolicyName:     policy.PolicyName,
			Severity:       policy.Severity,
			Category:       policy.Category,
			LastScanStatus: evaluationStatus,
			IssueId:        issueId,
		})
		policyViolations.SeverityInfos = buildSeverityInfo(severityCount)
		policyViolations.TotalPolicies = len(policies)
		policyViolations.TotalViolations = totalViolations
		totalSeverityWeights += severityWeights[policy.Severity]
		policyViolations.Compliance = calculateCompliancePercent(totalSeverityWeights, severityCount)
		policyViolations.Coverage = managed
	}

	return &policyViolations
}
