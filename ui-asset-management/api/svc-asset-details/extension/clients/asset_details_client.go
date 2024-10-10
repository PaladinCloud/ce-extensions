package clients

import (
	"context"
	"encoding/json"
	"fmt"
	"reflect"
	"strings"
	logger "svc-asset-details-layer/logging"
	"svc-asset-details-layer/models"
	"svc-asset-details-layer/utils"
)

type AssetDetailsClient struct {
	configuration       *Configuration
	elasticSearchClient *ElasticSearchClient
	rdsClient           *RdsClient
	log                 *logger.Logger
}

func NewAssetDetailsClient(configuration *Configuration, log *logger.Logger) *AssetDetailsClient {
	dynamodbClient := NewDynamoDBClient(configuration.Region, configuration.TenantConfigTable, configuration.TenantConfigTablePartitionKey)
	secretsClient := NewSecretsClient(configuration.Region)

	return &AssetDetailsClient{
		configuration:       configuration,
		elasticSearchClient: NewElasticSearchClient(dynamodbClient),
		rdsClient:           NewRdsClient(configuration, secretsClient),
		log:                 log,
	}
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

	c.log.Info("Starting to fetch asset details")
	result, err := c.elasticSearchClient.FetchAssetDetails(ctx, tenantId, allSources, assetId, 1)

	if err != nil {
		return nil, err
	}

	sourceArr := (*result)["hits"].(map[string]interface{})["hits"].([]interface{})
	if len(sourceArr) > 0 {
		c.log.Info("Found asset details for assetId: " + assetId)
		assetDetails := sourceArr[0].(map[string]interface{})["_source"].(map[string]interface{})
		mandatoryTags, _ := c.rdsClient.FetchMandatoryTags(ctx, tenantId)

		var primaryProvider string
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

		if val, present := assetDetails["primaryProvider"]; present {
			commonFields = c.buildCommonFields(assetDetails)
			primaryProvider = fmt.Sprintf("%v", val)
		} else if val, present := assetDetails["rawData"]; present {
			commonFields = c.buildCommonFields(assetDetails)
			primaryProvider = fmt.Sprintf("%v", val)
		} else {
			commonFields = c.buildCommonFieldsLegacy(assetDetails)
			primaryProvider, _ = c.buildPrimaryProviderForLegacyAssetModel(assetDetails)
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
			PrimaryProvider: primaryProvider,
		}, Message: success}, nil
	} else {
		c.log.Error("asset details not found for assetId: %s", assetId)
		return nil, fmt.Errorf("asset detials not found for assetId: %s", assetId)
	}
}

func (c *AssetDetailsClient) buildCommonFields(assetDetails map[string]interface{}) map[string]string {
	commonFields := map[string]string{}

	if v, ok := assetDetails["accountid"]; ok {
		commonFields[accountId] = v.(string)
	}
	if v, ok := assetDetails["accountname"]; ok {
		commonFields[accountName] = v.(string)
	}
	if v, ok := assetDetails[cloudType]; ok {
		commonFields[source] = v.(string)
	}
	if v, ok := assetDetails[region]; ok {
		commonFields[region] = v.(string)
	}
	if v, ok := assetDetails[sourceDisplayName]; ok {
		commonFields[sourceName] = v.(string)
	}
	if v, ok := assetDetails[entitytype]; ok {
		commonFields[targetType] = v.(string)
	}
	if v, ok := assetDetails["targetTypeDisplayName"]; ok {
		commonFields[targetTypeName] = v.(string)
	}

	return commonFields
}

func (c *AssetDetailsClient) buildCommonFieldsLegacy(assetDetails map[string]interface{}) map[string]string {
	commonFields := map[string]string{}

	if v, ok := assetDetails["accountid"]; ok {
		commonFields[accountId] = v.(string)
	}
	if v, ok := assetDetails["accountname"]; ok {
		commonFields[accountName] = v.(string)
	}
	if v, ok := assetDetails[cloudType]; ok {
		commonFields[source] = v.(string)
	}
	if v, ok := assetDetails[region]; ok {
		commonFields[region] = v.(string)
	}
	if v, ok := assetDetails[cloudType]; ok {
		commonFields[sourceName] = v.(string)
	}
	if v, ok := assetDetails[entitytype]; ok {
		commonFields[targetType] = v.(string)
	}
	if v, ok := assetDetails["targettypedisplayname"]; ok {
		commonFields[targetTypeName] = v.(string)
	}

	return commonFields
}

func (c *AssetDetailsClient) buildPrimaryProviderForLegacyAssetModel(assetDetails map[string]interface{}) (string, error) {
	for _, key := range utils.FieldsToBeSkipped {
		delete(assetDetails, key)
	}

	primaryProviderJson, err := json.Marshal(assetDetails)
	if err != nil {
		c.log.Error("Error while formatting legacy asset details to json string")
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
