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
	log                 *logger.Logger
}

func NewAssetDetailsClient(configuration *Configuration, log *logger.Logger) *AssetDetailsClient {
	return &AssetDetailsClient{dynamodbClient: NewDynamoDBClient(configuration, log), elasticSearchClient: NewElasticSearchClient(), log: log}
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

var fieldsToBeSkipped = [...]string{"_cloudType", "_resourceid", "_docid", "_discoverydate", "discoverydate", "firstdiscoveredon", "_entity", "_entitytype", "_loaddate", "assetRiskScore", "targettypedisplayname", "arsLoadDate"}

func (c *AssetDetailsClient) GetAssetDetails(ctx context.Context, tenantId, assetId string) (*models.AssetDetails, error) {
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
		var primaryProvider string
		var tags map[string]string
		var commonFields map[string]string
		if val, present := assetDetails["tags"]; present {
			tags = val.(map[string]string)
		} else {
			tags = c.buildTagsForLegacyAssetModel(assetDetails)
			for k := range tags {
				delete(assetDetails, "tags."+k)
			}
			assetDetails["tags"] = tags
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

		return &models.AssetDetails{
			AccountId:       commonFields[accountId],
			Source:          commonFields[source],
			SourceName:      commonFields[sourceName],
			TargetType:      commonFields[targetType],
			TargetTypeName:  commonFields[targetTypeName],
			Tags:            tags,
			PrimaryProvider: primaryProvider,
		}, nil
	} else {
		c.log.Error("asset detials not found for assetId: %s", assetId)
		return nil, fmt.Errorf("asset detials not found for assetId: %s", assetId)
	}
}

func (c *AssetDetailsClient) buildCommonFields(assetDetails map[string]interface{}) map[string]string {
	commonFields := map[string]string{}
	commonFields[accountId] = fmt.Sprintf("%v", assetDetails[accountId])
	commonFields[source] = assetDetails[source].(string)
	commonFields[sourceName] = assetDetails[sourceName].(string)
	commonFields[targetType] = assetDetails[targetType].(string)
	commonFields[targetTypeName] = assetDetails[targetTypeName].(string)
	return commonFields
}

func (c *AssetDetailsClient) buildCommonFieldsLegacy(assetDetails map[string]interface{}) map[string]string {
	commonFields := map[string]string{}
	commonFields[accountId] = fmt.Sprintf("%v", assetDetails["accountid"])
	commonFields[source] = assetDetails["_cloudType"].(string)
	commonFields[targetType] = assetDetails["_entitytype"].(string)
	commonFields[targetTypeName] = assetDetails["targettypedisplayname"].(string)
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
