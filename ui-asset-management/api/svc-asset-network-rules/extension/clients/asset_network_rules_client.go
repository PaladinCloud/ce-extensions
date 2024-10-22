package clients

import (
	"context"
	"fmt"
	"slices"
	"strings"
	"svc-asset-network-rules-layer/models"
)

type AssetNetworkRulesClient struct {
	elasticSearchClient *ElasticSearchClient
}

func NewAssetNetworkRulesClient(ctx context.Context, config *Configuration) *AssetNetworkRulesClient {
	dynamodbClient, _ := NewDynamoDBClient(ctx, config.UseAssumeRole, config.AssumeRoleArn, config.Region, config.TenantConfigOutputTable, config.TenantTablePartitionKey)
	return &AssetNetworkRulesClient{elasticSearchClient: NewElasticSearchClient(dynamodbClient)}
}

const (
	allSources = "all-sources"
	success    = "success"
	outbound   = "outbound"
	inbound    = "inbound"
	all        = "All"
	sg         = "sg"
	nsg        = "nsg"
	allow      = "Allow"
)

var targetTypesWithPortRules = []string{sg, nsg}

func (c *AssetNetworkRulesClient) GetPortRuleDetails(ctx context.Context, tenantId, targetType string, assetId string) (*models.Response, error) {
	if !slices.Contains(targetTypesWithPortRules, targetType) {
		return nil, fmt.Errorf("asset type does not have port rules feature")
	}

	if len(strings.TrimSpace(assetId)) == 0 {
		return nil, fmt.Errorf("assetId must be present")
	}

	fmt.Println("Starting to fetch port rules")
	var outboundRules []models.OutboundNetworkRule
	var inboundRules []models.InboundNetworkRule
	if targetType == nsg {
		result, err := c.elasticSearchClient.FetchAssetDetails(ctx, tenantId, allSources, assetId)
		if err != nil {
			return nil, err
		}

		assetDetails := getResults(result)
		if len(assetDetails) == 0 {
			return nil, fmt.Errorf("asset detials not found for asset id: %s", assetId)
		}

		assetDetail := assetDetails[0].(map[string]interface{})["_source"].(map[string]interface{})

		if v, ok := assetDetail["inBoundSecurityRules"]; ok {
			inboundSecurityRules := v.([]interface{})
			for _, rule := range inboundSecurityRules {
				ruleObj := rule.(map[string]interface{})
				protocol := ruleObj["protocol"].(string)
				if protocol == "*" {
					protocol = all
				}

				priority := fmt.Sprintf("%v", ruleObj["priority"])
				access := ruleObj["access"].(string)

				sourcePortRanges := ruleObj["sourcePortRanges"].([]interface{})
				fromPort := concatAsString(sourcePortRanges)

				destinationPortRanges := ruleObj["destinationPortRanges"].([]interface{})
				toPort := concatAsString(destinationPortRanges)

				sourceAddressPrefixes := ruleObj["sourceAddressPrefixes"].([]interface{})
				source := concatAsString(sourceAddressPrefixes)

				inboundRules = append(inboundRules, models.InboundNetworkRule{FromPort: fromPort, ToPort: toPort, Protocol: protocol, Source: source, Priority: priority, Access: access})
			}
		}

		if v, ok := assetDetail["outBoundSecurityRules"]; ok {
			outBoundSecurityRules := v.([]interface{})
			for _, rule := range outBoundSecurityRules {
				ruleObj := rule.(map[string]interface{})
				protocol := ruleObj["protocol"].(string)
				if protocol == "*" {
					protocol = all
				}

				priority := fmt.Sprintf("%v", ruleObj["priority"])
				access := ruleObj["access"].(string)

				sourcePortRanges := ruleObj["sourcePortRanges"].([]interface{})
				fromPort := concatAsString(sourcePortRanges)

				destinationPortRanges := ruleObj["destinationPortRanges"].([]interface{})
				toPort := concatAsString(destinationPortRanges)

				destinationAddressPrefixes := ruleObj["destinationAddressPrefixes"].([]interface{})
				destination := concatAsString(destinationAddressPrefixes)

				outboundRules = append(outboundRules, models.OutboundNetworkRule{FromPort: fromPort, ToPort: toPort, Protocol: protocol, Destination: destination, Priority: priority, Access: access})
			}
		}

		return &models.Response{Data: &models.NetworkRulesResponse{InboundRules: inboundRules, OutboundRules: outboundRules}, Message: success}, nil
	} else if targetType == sg {
		result, err := c.elasticSearchClient.FetchChildResourcesDetails(ctx, tenantId, allSources, targetType, assetId)
		if err != nil {
			return nil, err
		}

		sgRules := getResults(result)
		if len(sgRules) == 0 {
			return &models.Response{Data: &models.NetworkRulesResponse{InboundRules: []models.InboundNetworkRule{}, OutboundRules: []models.OutboundNetworkRule{}}, Message: success}, nil
		} else {
			for _, rule := range sgRules {
				ruleObj := rule.(map[string]interface{})["_source"].(map[string]interface{})
				ruleType := ruleObj["type"].(string)
				fromPort := ruleObj["fromport"].(string)
				if fromPort == "" {
					fromPort = all
				}
				toPort := ruleObj["toport"].(string)
				if toPort == "" {
					toPort = all
				}
				protocol := ruleObj["ipprotocol"].(string)
				cidrip := ruleObj["cidrip"].(string)
				if ruleType == outbound {
					outboundRules = append(outboundRules, models.OutboundNetworkRule{FromPort: fromPort, ToPort: toPort, Protocol: protocol, Destination: cidrip, Access: allow})
				} else if ruleType == inbound {
					inboundRules = append(inboundRules, models.InboundNetworkRule{FromPort: fromPort, ToPort: toPort, Protocol: protocol, Source: cidrip, Access: allow})
				}
			}

			return &models.Response{Data: &models.NetworkRulesResponse{InboundRules: inboundRules, OutboundRules: outboundRules}, Message: success}, nil
		}

	}

	return &models.Response{Data: nil, Message: "success"}, nil
}

func getResults(esResponse *map[string]interface{}) []interface{} {
	return (*esResponse)["hits"].(map[string]interface{})["hits"].([]interface{})
}

func concatAsString(arr []interface{}) string {
	if len(arr) > 0 {
		ports := make([]string, 0, len(arr))

		for _, port := range arr {
			if port == "*" {
				ports = append(ports, all)
			} else {
				ports = append(ports, port.(string))
			}
		}
		return strings.Join(ports, ",")
	}

	return ""
}
