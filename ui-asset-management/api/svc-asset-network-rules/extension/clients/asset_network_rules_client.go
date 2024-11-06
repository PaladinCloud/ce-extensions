package clients

import (
	"context"
	"fmt"
	"log"
	"slices"
	"strings"
	"svc-asset-network-rules-layer/models"
)

type AssetNetworkRulesClient struct {
	elasticSearchClient *ElasticSearchClient
}

func NewAssetNetworkRulesClient(ctx context.Context, config *Configuration) (*AssetNetworkRulesClient, error) {
	dynamodbClient, err := NewDynamoDBClient(ctx, config.UseAssumeRole, config.AssumeRoleArn, config.Region, config.TenantConfigOutputTable, config.TenantTablePartitionKey)
	if err != nil {
		return nil, fmt.Errorf("error creating dynamodb client %w", err)
	}

	opensearchClient := NewElasticSearchClient(dynamodbClient)
	return &AssetNetworkRulesClient{
		elasticSearchClient: opensearchClient,
	}, nil
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

const (
	inboundSecurityRulesField       = "inBoundSecurityRules"
	outboundSecurityRulesField      = "outBoundSecurityRules"
	protocolField                   = "protocol"
	priorityField                   = "priority"
	accessField                     = "access"
	sourcePortRangesField           = "sourcePortRanges"
	sourceAddressPrefixesField      = "sourceAddressPrefixes"
	destinationPortRangesField      = "destinationPortRanges"
	destinationAddressPrefixesField = "destinationAddressPrefixes"
	typeField                       = "type"
	fromPortField                   = "fromport"
	toPortField                     = "toport"
	ipProtocolField                 = "ipprotocol"
	cidrIpField                     = "cidrip"
)

var targetTypesWithPortRules = []string{sg, nsg}

func (c *AssetNetworkRulesClient) GetPortRuleDetails(ctx context.Context, tenantId, targetType string, assetId string) (*models.Response, error) {
	if !slices.Contains(targetTypesWithPortRules, targetType) {
		return nil, fmt.Errorf("asset type [%s] does not support port rules", targetType)
	}

	if len(strings.TrimSpace(assetId)) == 0 {
		return nil, fmt.Errorf("asset id must be present")
	}

	log.Printf("starting to fetch port rules for asset id [%s] and tenant id [%s]\n", assetId, tenantId)
	var outboundRules []models.OutboundNetworkRule
	var inboundRules []models.InboundNetworkRule
	if targetType == nsg {
		result, err := c.elasticSearchClient.FetchAssetDetails(ctx, tenantId, allSources, assetId)
		if err != nil {

			return nil, fmt.Errorf("error fetching asset details %w", err)
		}

		assetDetails, err := extractSourceFromResult(result, assetId)
		if err != nil {
			return nil, fmt.Errorf("failed to extract source from result: %w", err)
		}

		assetDetail, ok := assetDetails[0].(map[string]interface{})["_source"].(map[string]interface{})
		if !ok {
			return nil, fmt.Errorf("invalid source format for asset id [%s]", assetId)
		}

		if v, ok := assetDetail[inboundSecurityRulesField]; ok {
			inboundSecurityRules := v.([]interface{})
			for _, rule := range inboundSecurityRules {
				ruleObj := rule.(map[string]interface{})
				protocol := ruleObj[protocolField].(string)
				if protocol == "*" {
					protocol = all
				}

				priority := fmt.Sprintf("%v", ruleObj[priorityField])
				access := ruleObj[accessField].(string)

				sourcePortRanges := ruleObj[sourcePortRangesField].([]interface{})
				fromPort := concatAsString(sourcePortRanges)

				destinationPortRanges := ruleObj[destinationPortRangesField].([]interface{})
				toPort := concatAsString(destinationPortRanges)

				sourceAddressPrefixes := ruleObj[sourceAddressPrefixesField].([]interface{})
				source := concatAsString(sourceAddressPrefixes)

				inboundRules = append(inboundRules, models.InboundNetworkRule{
					FromPort: fromPort,
					ToPort:   toPort,
					Protocol: protocol,
					Source:   source,
					Priority: priority,
					Access:   access})
			}
		}

		if v, ok := assetDetail[outboundSecurityRulesField]; ok {
			outBoundSecurityRules := v.([]interface{})
			for _, rule := range outBoundSecurityRules {
				ruleObj := rule.(map[string]interface{})
				protocol := ruleObj[protocolField].(string)
				if protocol == "*" {
					protocol = all
				}

				priority := fmt.Sprintf("%v", ruleObj[priorityField])
				access := ruleObj[accessField].(string)

				sourcePortRanges := ruleObj[sourcePortRangesField].([]interface{})
				fromPort := concatAsString(sourcePortRanges)

				destinationPortRanges := ruleObj[destinationPortRangesField].([]interface{})
				toPort := concatAsString(destinationPortRanges)

				destinationAddressPrefixes := ruleObj[destinationAddressPrefixesField].([]interface{})
				destination := concatAsString(destinationAddressPrefixes)

				outboundRules = append(outboundRules, models.OutboundNetworkRule{
					FromPort:    fromPort,
					ToPort:      toPort,
					Protocol:    protocol,
					Destination: destination,
					Priority:    priority,
					Access:      access})
			}
		}

		return &models.Response{Data: &models.NetworkRulesResponse{
			InboundRules:  inboundRules,
			OutboundRules: outboundRules},
			Message: success,
		}, nil
	} else if targetType == sg {
		result, err := c.elasticSearchClient.FetchChildResourcesDetails(ctx, tenantId, allSources, targetType, assetId)
		if err != nil {
			return nil, fmt.Errorf("error fetching child resources details %w", err)
		}

		sgRules, err := extractSourceFromResult(result, assetId)
		if err != nil {
			return nil, fmt.Errorf("failed to extract source [security group rules] from result: %w", err)
		}

		if len(sgRules) == 0 {
			return &models.Response{Data: &models.NetworkRulesResponse{
				InboundRules:  []models.InboundNetworkRule{},
				OutboundRules: []models.OutboundNetworkRule{}},
				Message: success,
			}, nil
		} else {
			for _, rule := range sgRules {
				ruleObj := rule.(map[string]interface{})["_source"].(map[string]interface{})
				ruleType := ruleObj[typeField].(string)
				fromPort := ruleObj[fromPortField].(string)
				if fromPort == "" {
					fromPort = all
				}

				toPort := ruleObj[toPortField].(string)
				if toPort == "" {
					toPort = all
				}

				protocol := ruleObj[ipProtocolField].(string)
				cidrIp := ruleObj[cidrIpField].(string)
				if ruleType == outbound {
					outboundRules = append(outboundRules, models.OutboundNetworkRule{
						FromPort:    fromPort,
						ToPort:      toPort,
						Protocol:    protocol,
						Destination: cidrIp,
						Access:      allow})
				} else if ruleType == inbound {
					inboundRules = append(inboundRules, models.InboundNetworkRule{
						FromPort: fromPort,
						ToPort:   toPort,
						Protocol: protocol,
						Source:   cidrIp,
						Access:   allow})
				}
			}

			return &models.Response{Data: &models.NetworkRulesResponse{InboundRules: inboundRules, OutboundRules: outboundRules}, Message: success}, nil
		}
	}

	return &models.Response{Data: nil, Message: "success"}, nil
}

func extractSourceFromResult(result *map[string]interface{}, assetId string) ([]interface{}, error) {
	hits, ok := (*result)["hits"].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("unexpected response format: 'hits' key missing")
	}

	sourceArr, ok := hits["hits"].([]interface{})
	if !ok || len(sourceArr) == 0 {
		return nil, fmt.Errorf("asset details not found for asset id [%s]", assetId)
	}

	return sourceArr, nil
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
