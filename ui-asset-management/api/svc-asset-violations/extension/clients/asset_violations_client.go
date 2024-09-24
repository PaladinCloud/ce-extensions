package clients

import (
	"context"
	"fmt"
	"math"
	"strconv"
	"strings"
	logger "svc-asset-violations-layer/logging"
	"svc-asset-violations-layer/models"
)

type AssetViolationsClient struct {
	dynamodbClient      *DynamodbClient
	elasticSearchClient *ElasticSearchClient
	rdsClient           *RdsClient
	log                 *logger.Logger
}

func NewAssetViolationsClient(configuration *Configuration, log *logger.Logger) *AssetViolationsClient {
	return &AssetViolationsClient{dynamodbClient: NewDynamoDBClient(configuration, log), elasticSearchClient: NewElasticSearchClient(), rdsClient: NewRdsClient(configuration), log: log}
}

const (
	docIDKeyword   = "_docid.keyword"
	docTypeKeyword = "docType.keyword"
	cloudType      = "_cloudType"
	source         = "source"
	sourceName     = "sourceName"
	targetType     = "targetType"
	targetTypeName = "targetTypeName"
	accountId      = "accountId"
	allSources     = "all-sources"
	open           = "open"
	fail           = "Fail"
	exempted       = "exempted"
	exempt         = "Exempt"
	pass           = "Pass"
	issueStatus    = "issueStatus"
	policyId       = "policyId"
	success        = "success"
)

var severities = [4]string{"low", "medium", "high", "critical"}
var severityWeights = map[string]int{"low": 1, "medium": 3, "high": 5, "critical": 10}

func (c *AssetViolationsClient) GetAssetViolations(ctx context.Context, targetType string, tenantId, assetId string) (*models.AssetViolations, error) {
	if len(strings.TrimSpace(assetId)) == 0 {
		return nil, fmt.Errorf("assetId must be present")
	}
	if len(strings.TrimSpace(targetType)) == 0 {
		return nil, fmt.Errorf("targetType must be present")
	}
	// fetch all the relevant policies for the target type
	policies, err := c.rdsClient.GetPolicies(ctx, targetType)
	if err != nil {
		c.log.Error("error fetching policies from rds for target type: " + targetType)
		return nil, fmt.Errorf("error fetching policies from rds for target type: " + targetType)
	}
	if policies == nil || len(policies) == 0 {
		c.log.Error("No policies for given target type: " + targetType)
		return nil, fmt.Errorf("No policies for given target type: " + targetType)
	}
	c.log.Info("got " + strconv.Itoa(len(policies)) + " policies for target type: " + targetType)

	esDomainProperties, err := c.dynamodbClient.GetEsDomain(ctx, tenantId)
	if err != nil {
		return nil, err
	}

	// fetch violations for the asset
	result, err := c.elasticSearchClient.FetchAssetViolations(ctx, esDomainProperties, allSources, assetId)
	if err != nil {
		return nil, err
	}

	sourceArr := (*result)["hits"].(map[string]interface{})["hits"].([]interface{})
	var policyViolation map[string]interface{}
	if len(sourceArr) > 0 {
		c.log.Info("found " + strconv.Itoa(len(sourceArr)) + " violations for assetId: " + assetId)

		policyViolation = make(map[string]interface{}, len(sourceArr))
		// put the violations in map with policyId as key, so that they can be retrieved at constant time when building response with all policies
		for i := 0; i < len(sourceArr); i++ {
			violationDetail := sourceArr[i].(map[string]interface{})["_source"].(map[string]interface{})
			violationDetail["_id"] = sourceArr[i].(map[string]interface{})["_id"]
			policyViolation[violationDetail[policyId].(string)] = violationDetail
		}
	}

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

		if policyViolation[policy.PolicyId] != nil {
			violationInfo := policyViolation[policy.PolicyId].(map[string]interface{})
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
	}
	return &models.AssetViolations{Data: policyViolations, Message: success}, nil
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
