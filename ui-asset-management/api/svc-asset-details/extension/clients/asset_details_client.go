package clients

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"reflect"
	"strings"
	"svc-asset-details-layer/models"
	"svc-asset-details-layer/utils"
)

type AssetDetailsClient struct {
	configuration       *Configuration
	elasticSearchClient *ElasticSearchClient
	rdsClient           *RdsClient
}

func NewAssetDetailsClient(ctx context.Context, config *Configuration) (*AssetDetailsClient, error) {
	dynamodbClient, err := NewDynamoDBClient(ctx, config.UseAssumeRole, config.AssumeRoleArn, config.Region, config.TenantConfigOutputTable, config.TenantTablePartitionKey)
	if err != nil {
		return nil, err
	}

	secretsClient, err := NewSecretsClient(ctx, config.UseAssumeRole, config.AssumeRoleArn, config.Region)
	if err != nil {
		return nil, err
	}

	opensearchClient := NewElasticSearchClient(dynamodbClient)
	rdsClient, err := NewRdsClient(secretsClient, config.SecretIdPrefix)
	if err != nil {
		return nil, err
	}

	return &AssetDetailsClient{
		configuration:       config,
		elasticSearchClient: opensearchClient,
		rdsClient:           rdsClient,
	}, nil
}

// TODO: migrate to asset model v2 when available
const (
	allSources        = "all-sources"
	cloudType         = "_cloudType"
	source            = "source"
	sourceName        = "sourceName"
	sourceDisplayName = "sourceDisplayName"
	targetType        = "targetType"
	targetTypeName    = "targetTypeName"
	region            = "region"
	accountId         = "accountId"
	accountName       = "accountName"
	entitytype        = "_entitytype"
	success           = "success"
	unknown           = "Unknown"
)

func (c *AssetDetailsClient) GetAssetDetails(ctx context.Context, tenantId, assetId string) (*models.Response, error) {
	if len(strings.TrimSpace(assetId)) == 0 {
		return nil, fmt.Errorf("assetId must be present")
	}

	log.Println("starting to fetch asset details")
	result, err := c.elasticSearchClient.FetchAssetDetails(ctx, tenantId, allSources, assetId, 1)

	if err != nil {
		return nil, err
	}

	sourceArr := (*result)["hits"].(map[string]interface{})["hits"].([]interface{})
	if len(sourceArr) > 0 {
		log.Printf("found asset details for asset id: %s\n", assetId)
		assetDetails := sourceArr[0].(map[string]interface{})["_source"].(map[string]interface{})
		mandatoryTags, err := c.rdsClient.FetchMandatoryTags(ctx, tenantId)
		if err != nil {
			return nil, fmt.Errorf("failed to fetch mandatory tags: %w", err)
		}

		var tags map[string]string
		var commonFields map[string]string
		if val, present := assetDetails["tags"]; present && reflect.ValueOf(assetDetails["tags"]).Kind() == reflect.Map {
			tagsMap := val.(map[string]interface{})
			tags = make(map[string]string)
			for k, v := range tagsMap {
				tags[k] = v.(string)
			}
		} else {
			tags = c.buildTagsForLegacyAssetModel(assetDetails)
			for k := range tags {
				delete(assetDetails, "tags."+k)
			}
			assetDetails["tags"] = tags
		}

		mandatoryTagsWIthValues := make(map[string]string)
		if mandatoryTags != nil {
			for _, mandatoryTag := range mandatoryTags {
				if v, ok := tags[mandatoryTag.TagName]; !ok {
					tags[mandatoryTag.TagName] = unknown
					mandatoryTagsWIthValues[mandatoryTag.TagName] = unknown
				} else {
					mandatoryTagsWIthValues[mandatoryTag.TagName] = v
				}
			}
		}

		var primaryProvider string
		if val, present := assetDetails["primaryProvider"]; present {
			commonFields = c.buildCommonFields(assetDetails)
			primaryProvider = fmt.Sprintf("%v", val)
		} else if v, p := assetDetails["rawData"]; p {
			commonFields = c.buildCommonFields(assetDetails)
			primaryProvider = fmt.Sprintf("%v", v)
		} else {
			commonFields = c.buildCommonFieldsLegacy(assetDetails)
			primaryProviderModel, err := c.buildPrimaryProviderForLegacyAssetModel(assetDetails)
			if err != nil {
				return nil, fmt.Errorf("failed to build primary provider: %w", err)
			}

			primaryProvider = primaryProviderModel
		}

		return &models.Response{Data: models.AssetDetails{
			AccountId:       commonFields[accountId],
			AccountName:     commonFields[accountName],
			Source:          commonFields[source],
			SourceName:      commonFields[sourceName],
			TargetType:      commonFields[targetType],
			TargetTypeName:  commonFields[targetTypeName],
			Tags:            tags,
			MandatoryTags:   mandatoryTagsWIthValues,
			Region:          commonFields[region],
			PrimaryProvider: primaryProvider,
		}, Message: success}, nil
	} else {
		return nil, fmt.Errorf("asset detials not found for asset id: %s", assetId)
	}
}

func (c *AssetDetailsClient) buildCommonFields(assetDetails map[string]interface{}) map[string]string {
	commonFields := map[string]string{}

	if v, ok := assetDetails["accountid"]; ok && v != nil {
		commonFields[accountId] = v.(string)
	}
	if v, ok := assetDetails["accountname"]; ok && v != nil {
		commonFields[accountName] = v.(string)
	}
	if v, ok := assetDetails[cloudType]; ok && v != nil {
		commonFields[source] = v.(string)
	}
	if v, ok := assetDetails[region]; ok && v != nil {
		commonFields[region] = v.(string)
	}
	if v, ok := assetDetails[sourceDisplayName]; ok && v != nil {
		commonFields[sourceName] = v.(string)
	}
	if v, ok := assetDetails[entitytype]; ok && v != nil {
		commonFields[targetType] = v.(string)
	}
	if v, ok := assetDetails["targetTypeDisplayName"]; ok && v != nil {
		commonFields[targetTypeName] = v.(string)
	}

	return commonFields
}

func (c *AssetDetailsClient) buildCommonFieldsLegacy(assetDetails map[string]interface{}) map[string]string {
	commonFields := c.buildCommonFields(assetDetails)

	if v, ok := assetDetails[cloudType]; ok {
		commonFields[sourceName] = v.(string)
	}

	return commonFields
}

func (c *AssetDetailsClient) buildPrimaryProviderForLegacyAssetModel(assetDetails map[string]interface{}) (string, error) {
	for _, key := range utils.FieldsToBeSkipped {
		delete(assetDetails, key)
	}

	primaryProviderJson, err := json.Marshal(assetDetails)
	if err != nil {
		return "", err
	}

	return string(primaryProviderJson[:]), nil
}

func (c *AssetDetailsClient) buildTagsForLegacyAssetModel(assetDetails map[string]interface{}) map[string]string {
	tagsKvPairs := map[string]string{}
	source := assetDetails[cloudType]
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
