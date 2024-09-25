package clients

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	logger "svc-asset-details-layer/logging"
	"svc-asset-details-layer/models"
)

type AssetDetailsClient struct {
	dynamodbClient      *DynamodbClient
	elasticSearchClient *ElasticSearchClient
	rdsClient           *RdsClient
	log                 *logger.Logger
}

func NewAssetDetailsClient(configuration *Configuration, log *logger.Logger) *AssetDetailsClient {
	return &AssetDetailsClient{dynamodbClient: NewDynamoDBClient(configuration, log), elasticSearchClient: NewElasticSearchClient(), rdsClient: NewRdsClient(configuration), log: log}
}

const (
	docIDKeyword      = "_docid.keyword"
	docTypeKeyword    = "docType.keyword"
	cloudType         = "_cloudType"
	source            = "source"
	sourceName        = "sourceName"
	targetType        = "targetType"
	targetTypeName    = "targetTypeName"
	region            = "region"
	accountId         = "accountId"
	accountName       = "accountName"
	allSources        = "all-sources"
	sourceDisplayName = "sourceDisplayName"
	entitytype        = "_entitytype"
	success           = "success"
	unknown           = "Unknown"
	mandatory         = "mandatory"
	optional          = "optional"
)

var fieldsToBeSkipped = [...]string{"_cloudType", "_resourceid", "_docid", "_discoverydate", "discoverydate", "firstdiscoveredon", "_entity", "_entitytype", "_loaddate", "assetRiskScore", "targettypedisplayname", "arsLoadDate"}

func (c *AssetDetailsClient) GetAssetDetails(ctx context.Context, tenantId, assetId string) (*models.Response, error) {
	if len(strings.TrimSpace(assetId)) == 0 {
		return nil, fmt.Errorf("assetId must be present")
	}

	esDomainProperties, err := c.dynamodbClient.GetEsDomain(ctx, tenantId)
	if err != nil {
		return nil, err
	}

	c.log.Info("Starting to fetch asset details")
	result, err := c.elasticSearchClient.FetchAssetDetails(ctx, esDomainProperties, allSources, assetId, 1)

	if err != nil {
		return nil, err
	}

	sourceArr := (*result)["hits"].(map[string]interface{})["hits"].([]interface{})
	if len(sourceArr) > 0 {
		c.log.Info("Found asset details for assetId: " + assetId)
		assetDetails := sourceArr[0].(map[string]interface{})["_source"].(map[string]interface{})
		mandatoryTags, _ := c.rdsClient.FetchMandatoryTags(ctx)
		var primaryProvider string
		var tags map[string]string
		var commonFields map[string]string
		if val, present := assetDetails["tags"]; present {
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

		if mandatoryTags != nil {
			for _, mandatoryTag := range mandatoryTags {
				if _, ok := tags[mandatoryTag.TagName]; !ok {
					tags[mandatoryTag.TagName] = unknown
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
			PrimaryProvider: primaryProvider,
		}, Message: success}, nil
	} else {
		c.log.Error("asset detials not found for assetId: %s", assetId)
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
	for _, key := range fieldsToBeSkipped {
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
