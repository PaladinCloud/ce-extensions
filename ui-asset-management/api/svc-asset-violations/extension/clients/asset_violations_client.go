package clients

import (
	"context"
	"fmt"
	"strconv"
	"strings"
	logger "svc-asset-violations-layer/logging"
	"svc-asset-violations-layer/models"
)

type AssetViolationsClient struct {
	elasticSearchClient *ElasticSearchClient
	rdsClient           *RdsClient
	log                 *logger.Logger
}

func NewAssetViolationsClient(configuration *Configuration, log *logger.Logger) *AssetViolationsClient {
	return &AssetViolationsClient{elasticSearchClient: NewElasticSearchClient(), rdsClient: NewRdsClient(configuration), log: log}
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
)

func (c *AssetViolationsClient) GetAssetViolations(ctx context.Context, targetType string, assetId string) (*models.AssetViolations, error) {
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

	// fetch violations for the asset
	result, err := c.elasticSearchClient.FetchAssetViolations(ctx, allSources, assetId)
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
			policyViolation[violationDetail["policyId"].(string)] = violationDetail
		}
	}
	response := models.AssetViolations{Violations: make([]models.Violation, len(policies))}

	for _, policy := range policies {
		violationInfo := policyViolation[policy.PolicyId].(map[string]string)
		response.Violations = append(response.Violations, models.Violation{
			PolicyName:     policy.PolicyName,
			Severity:       policy.Severity,
			Category:       policy.Category,
			LastScanStatus: violationInfo["issueStatus"],
			IssueId:        violationInfo["_id"],
		})

	}
	return &response, nil
}
