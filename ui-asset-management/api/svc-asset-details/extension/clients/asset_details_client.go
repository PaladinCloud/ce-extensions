package clients

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"strings"
	"svc-asset-details-layer/constants"
	"svc-asset-details-layer/legacy_constants"
	"svc-asset-details-layer/models"
)

type AssetDetailsClient struct {
	configuration       *Configuration
	elasticSearchClient *ElasticSearchClient
	rdsClient           *RdsClient
}

func NewAssetDetailsClient(ctx context.Context, config *Configuration) (*AssetDetailsClient, error) {
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

	return &AssetDetailsClient{
		configuration:       config,
		elasticSearchClient: opensearchClient,
		rdsClient:           rdsClient,
	}, nil
}

// TODO: migrate to asset model v2 when available
const (
	allSources = "all-sources"
	success    = "success"
	empty      = "<missing>"
)

func (c *AssetDetailsClient) GetAssetDetails(ctx context.Context, tenantId, assetId string) (*models.Response, error) {
	if strings.TrimSpace(assetId) == "" {
		return nil, fmt.Errorf("asset id must be present")
	}

	log.Println("starting to fetch asset details")
	result, err := c.elasticSearchClient.FetchAssetDetails(ctx, tenantId, allSources, assetId, 1)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch asset details: %w", err)
	}

	assetDetails, err := extractSourceFromResult(result, assetId)
	if err != nil {
		return nil, fmt.Errorf("failed to extract source from result: %w", err)
	}

	mandatoryTagsFromDb, err := c.rdsClient.FetchMandatoryTags(ctx, tenantId)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch mandatory tags: %w", err)
	}

	tags, err := c.extractTags(assetDetails, mandatoryTagsFromDb)
	if err != nil {
		return nil, fmt.Errorf("failed to extract tags: %w", err)
	}

	message := success
	isLegacy, primaryProvider, err := c.extractPrimaryProvider(assetDetails)
	commonFields := make(map[string]string)
	if isLegacy {
		commonFields = c.buildLegacyCommonFields(assetDetails)
	} else {
		commonFields = c.buildCommonFields(assetDetails)
		if err != nil {
			message = err.Error()
		}
	}

	mandatoryTags, err := c.extractMandatoryTags(ctx, tenantId, tags, mandatoryTagsFromDb)
	if err != nil {
		return nil, fmt.Errorf("failed to add mandatory tags: %w", err)
	}

	response := &models.Response{
		Data: models.AssetDetails{
			AccountId:       commonFields[constants.AccountId],
			AccountName:     commonFields[constants.AccountName],
			Source:          commonFields[constants.Source],
			SourceName:      commonFields[constants.SourceName],
			TargetType:      commonFields[constants.TargetType],
			TargetTypeName:  commonFields[constants.TargetTypeName],
			Region:          commonFields[constants.Region],
			AssetState:      commonFields[constants.AssetState],
			Tags:            tags,
			MandatoryTags:   mandatoryTags,
			PrimaryProvider: primaryProvider,
			ResourceId:      commonFields[constants.ResourceId],
		},
		Message: message,
	}

	return response, nil
}

func extractSourceFromResult(result *map[string]interface{}, assetId string) (map[string]interface{}, error) {
	hits, ok := (*result)["hits"].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("unexpected response format: 'hits' key missing")
	}

	sourceArr, ok := hits["hits"].([]interface{})
	if !ok || len(sourceArr) == 0 {
		return nil, fmt.Errorf("asset details not found for asset id [%s]", assetId)
	}

	source, ok := sourceArr[0].(map[string]interface{})["_source"].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("invalid source format for asset id [%s]", assetId)
	}

	return source, nil
}

// Helper method to extract tags
func (c *AssetDetailsClient) extractTags(assetDetails map[string]interface{}, mandatoryTags []models.Tag) (map[string]string, error) {
	tags := make(map[string]string)
	if val, ok := assetDetails["tags"].(map[string]interface{}); ok {
		for k, v := range val {
			if strVal, ok2 := v.(string); ok2 {
				tagDisplayName := getTagDisplayName(k, mandatoryTags)
				tags[tagDisplayName] = strVal
			}
		}
	} else {
		tags = c.buildTagsForLegacyAssetModel(assetDetails)
		assetDetails["tags"] = tags
	}

	return tags, nil
}

func getTagDisplayName(tag string, mandatoryTags []models.Tag) string {
	for _, mandatoryTag := range mandatoryTags {
		if strings.ToLower(tag) == strings.ToLower(mandatoryTag.TagName) {
			return mandatoryTag.TagName
		}
	}
	return tag
}

// Helper method to add mandatory tags
func (c *AssetDetailsClient) extractMandatoryTags(ctx context.Context, tenantId string, tags map[string]string, mandatoryTags []models.Tag) (map[string]string, error) {
	mandatoryTagsWithValues := make(map[string]string)

	for _, mandatoryTag := range mandatoryTags {
		if val, exists := tags[mandatoryTag.TagName]; exists {
			mandatoryTagsWithValues[mandatoryTag.TagName] = val
		} else {
			tags[mandatoryTag.TagName] = empty
			mandatoryTagsWithValues[mandatoryTag.TagName] = empty
		}
	}

	return mandatoryTagsWithValues, nil
}

func (c *AssetDetailsClient) buildTagsForLegacyAssetModel(assetDetails map[string]interface{}) map[string]string {
	tagsKvPairs := map[string]string{}
	source := assetDetails[legacy_constants.Source]
	for key, value := range assetDetails {
		tagsPrefix := "tags."
		if strings.HasPrefix(key, tagsPrefix) {
			tagKey := key[len(tagsPrefix):]
			if source == "gcp" {
				tagKey = strings.ToLower(tagKey)
			}

			tagsKvPairs[tagKey] = value.(string)
		}
	}

	return tagsKvPairs
}

func (c *AssetDetailsClient) buildCommonFields(assetDetails map[string]interface{}) map[string]string {
	commonFields := make(map[string]string)
	for _, field := range constants.CommonFields {
		value := c.getCommonField(assetDetails, field)
		if value != "" {
			commonFields[field] = fmt.Sprintf("%v", value)
		}
	}

	return commonFields
}

func (c *AssetDetailsClient) buildLegacyCommonFields(assetDetails map[string]interface{}) map[string]string {
	commonFields := make(map[string]string)
	for _, field := range legacy_constants.CommonFields {
		value := c.getCommonField(assetDetails, field)
		if value != "" {
			switch field {
			case legacy_constants.AccountId:
				commonFields[constants.AccountId] = fmt.Sprintf("%v", value)
			case legacy_constants.AccountName:
				commonFields[constants.AccountName] = fmt.Sprintf("%v", value)
			case legacy_constants.Source:
				commonFields[constants.Source] = fmt.Sprintf("%v", value)
				commonFields[constants.SourceName] = fmt.Sprintf("%v", value)
			case legacy_constants.TargetType:
				commonFields[constants.TargetType] = fmt.Sprintf("%v", value)
			case legacy_constants.TargetTypeName:
				commonFields[constants.TargetTypeName] = fmt.Sprintf("%v", value)
			case legacy_constants.Region:
				commonFields[constants.Region] = fmt.Sprintf("%v", value)
			case legacy_constants.AssetState:
				commonFields[constants.AssetState] = fmt.Sprintf("%v", value)
			case legacy_constants.ResourceId:
				commonFields[constants.ResourceId] = fmt.Sprintf("%v", value)
			}
		}
	}

	return commonFields
}

func (c *AssetDetailsClient) getCommonField(assetDetails map[string]interface{}, field string) interface{} {
	if v, ok := assetDetails[field]; ok {
		return v
	}

	return ""
}

// Helper method to determine primary provider
func (c *AssetDetailsClient) extractPrimaryProvider(assetDetails map[string]interface{}) (bool, string, error) {
	isLegacy := false
	// If asset state is suspicious, return empty primary provider
	if assetState, ok := assetDetails[constants.AssetState]; ok {
		if assetState == constants.Suspicious {
			return isLegacy, fmt.Sprintf("%v", ""), errors.New("No primary provider data exists")
		}
	}

	if provider, ok := assetDetails[constants.PrimaryProvider]; ok {
		return isLegacy, fmt.Sprintf("%v", provider), nil
	}

	isLegacy = true
	legacyPrimaryProvider := c.buildLegacyPrimaryProvider(assetDetails)
	legacyPrimaryProviderJson, err := json.Marshal(legacyPrimaryProvider)
	if err != nil {
		return isLegacy, "", fmt.Errorf("failed to marshal legacy primary provider %w", err)
	}

	return isLegacy, string(legacyPrimaryProviderJson), nil
}

func (c *AssetDetailsClient) buildLegacyPrimaryProvider(assetDetails map[string]interface{}) map[string]interface{} {
	primaryProvider := make(map[string]interface{})

	// Copy only keys that are not in LegacyCommonFields
	for key, value := range assetDetails {
		if !legacy_constants.IsCommonField(key) {
			primaryProvider[key] = value
		}
	}

	return primaryProvider
}
