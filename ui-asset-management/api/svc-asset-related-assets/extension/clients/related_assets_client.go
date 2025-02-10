package clients

import (
	"context"
	"fmt"
	"log"
	"os"
	"strings"
	"svc-asset-related-assets-layer/models"
)

type RelatedAssetsClient struct {
	elasticSearchClient *ElasticSearchClient
}

func NewRelatedAssetsClient(ctx context.Context, config *Configuration) (*RelatedAssetsClient, error) {
	dynamodbClient, err := NewDynamoDBClient(ctx, config.UseAssumeRole, config.AssumeRoleArn, config.Region, config.TenantConfigOutputTable, config.TenantTablePartitionKey)
	if err != nil {
		return nil, fmt.Errorf("error creating dynamodb client %w", err)
	}

	opensearchClient := NewElasticSearchClient(dynamodbClient)
	return &RelatedAssetsClient{
		elasticSearchClient: opensearchClient,
	}, nil
}

const (
	allSources                       = "all-sources"
	success                          = "success"
	publicIpAddress                  = "publicipaddress"
	iamInstanceProfileArn            = "iaminstanceprofilearn"
	targetTypeRelatedAssetsEnv       = "TARGET_TYPE_WITH_RELATED_ASSETS"
	instanceid                       = "instanceid"
	doctypeSecGroup                  = "ec2_secgroups"
	doctypeBlockDevices              = "ec2_blockdevices"
	docType                          = "docType"
	sg                               = "sg"
	volume                           = "volume"
	publicIpAddressDisplayName       = "Public IPs"
	iamInstanceProfileArnDisplayName = "Instance Roles"
	relatedAssets                    = "relatedAssets"
)

var (
	defaultTargetTypeWithRelatedAssets = [1]string{"ec2"}
	targetTypeWithRelatedAssets        = os.Getenv(targetTypeRelatedAssetsEnv)
	relatedAssetDocTypes               = map[string][]string{"ec2": []string{doctypeSecGroup, doctypeBlockDevices}}
)

func (c *RelatedAssetsClient) GetRelatedAssetsDetails(ctx context.Context, tenantId, targetType, assetId string) (*models.Response, error) {
	if len(strings.TrimSpace(assetId)) == 0 {
		return nil, fmt.Errorf("asset id must be present")
	}

	// related assets like Public Ip and Iam instance profile ARN are present in the asset details doc
	log.Println("fetching asset details")
	result, err := c.elasticSearchClient.FetchAssetDetails(ctx, tenantId, allSources, assetId)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch asset details %w", err)
	}

	assetDetails, err := extractSourceFromResult(result, assetId)
	if err != nil {
		return nil, fmt.Errorf("failed to extract source from result: %w", err)
	}

	allRelatedAssets := []models.RelatedAsset{}
	if v, ok := assetDetails[instanceid]; ok {

		// fetch related assets for the asset
		log.Println("fetching related assets")
		esResponse, err2 := c.elasticSearchClient.FetchRelatedAssets(ctx, relatedAssetDocTypes[targetType], tenantId, allSources, v.(string))
		if err2 != nil {
			return nil, fmt.Errorf("failed to fetch related assets %w", err2)
		}

		for _, response := range (*esResponse)["responses"].([]interface{}) {
			responseDetailMap := response.(map[string]interface{})
			relatedAssetArray := getResults(&responseDetailMap)
			for _, relatedAssetDoc := range relatedAssetArray {
				relatedAssetDetails := relatedAssetDoc.(map[string]interface{})["_source"].(map[string]interface{})
				if relatedAssetDetails[docType] == doctypeSecGroup {
					relatedAsset := models.RelatedAsset{ResourceId: relatedAssetDetails["securitygroupid"].(string), AssetType: sg}
					allRelatedAssets = append(allRelatedAssets, relatedAsset)
				} else if relatedAssetDetails[docType] == doctypeBlockDevices {
					relatedAsset := models.RelatedAsset{ResourceId: relatedAssetDetails["volumeid"].(string), AssetType: volume}
					allRelatedAssets = append(allRelatedAssets, relatedAsset)
				}

			}
		}
	}

	if v, ok := assetDetails[relatedAssets]; ok {
		relatedAssetIds := v.([]interface{})
		for _, relatedAssetId := range relatedAssetIds {
			relatedAsset := models.RelatedAsset{AssetId: relatedAssetId.(string)}
			allRelatedAssets = append(allRelatedAssets, relatedAsset)
		}
	}

	// fetch related parent assets
	parentRelatedAssetDetails, err := c.elasticSearchClient.FetchParentRelatedAssets(ctx, tenantId, allSources, assetId)
	if err != nil {
		fmt.Errorf("failed to fetch parent related asset details %w", err)
	}
	resultArr, err := extractResultArray(parentRelatedAssetDetails)
	if err != nil {
		fmt.Errorf("failed to extract parent related asset details %w", err)
	}
	for _, result := range resultArr {

		source, ok := result.(map[string]interface{})["_source"].(map[string]interface{})
		if !ok {
			return nil, fmt.Errorf("invalid source format")
		}
		documentId := source[docId].(string)
		relatedAsset := models.RelatedAsset{AssetId: documentId}
		allRelatedAssets = append(allRelatedAssets, relatedAsset)
	}

	if len(allRelatedAssets) > 0 {
		// fetch assetId and other info for the related assets from the related assets' own details
		log.Println("fetching asset details of related assets")
		relatedAssetDetails, err2 := c.elasticSearchClient.FetchMultipleAssetsByResourceId(ctx, tenantId, allSources, allRelatedAssets)
		if err2 != nil {
			return nil, fmt.Errorf("failed to fetch related asset details %w", err2)
		}

		for _, response := range (*relatedAssetDetails)["responses"].([]interface{}) {
			responseDetailMap := response.(map[string]interface{})
			relatedAssetArray := getResults(&responseDetailMap)
			for _, relatedAssetDoc := range relatedAssetArray {
				relatedAsset := relatedAssetDoc.(map[string]interface{})["_source"].(map[string]interface{})
				assetTypeName := relatedAsset[targetTypeDisplayName].(string)
				documentId := relatedAsset[docId].(string)
				assetType := relatedAsset[docType].(string)
				resourceId := relatedAsset[_resourceId].(string)
				for i, relatedAsset := range allRelatedAssets {
					if relatedAsset.AssetId == documentId || (relatedAsset.AssetType == assetType && relatedAsset.ResourceId == resourceId) {
						allRelatedAssets[i].AssetId = documentId
						allRelatedAssets[i].AssetTypeName = assetTypeName
						allRelatedAssets[i].AssetType = assetType
						allRelatedAssets[i].ResourceId = resourceId
					}
				}
			}
		}
	}

	if _, ok := assetDetails[instanceid]; ok {
		if v, ok := assetDetails[publicIpAddress]; ok && v != "" {
			publicIpAsset := models.RelatedAsset{ResourceId: v.(string), AssetTypeName: publicIpAddressDisplayName}
			allRelatedAssets = append(allRelatedAssets, publicIpAsset)
		}
		if v, ok := assetDetails[iamInstanceProfileArn]; ok && v != "" {
			iamInstanceProfileArnAsset := models.RelatedAsset{ResourceId: v.(string), AssetTypeName: iamInstanceProfileArnDisplayName}
			allRelatedAssets = append(allRelatedAssets, iamInstanceProfileArnAsset)
		}
	}

	return &models.Response{Data: &models.RelatedAssets{AllRelatedAssets: &allRelatedAssets}, Message: success}, nil
}

func getResults(esResponse *map[string]interface{}) []interface{} {
	return (*esResponse)["hits"].(map[string]interface{})["hits"].([]interface{})
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

func extractResultArray(result *map[string]interface{}) ([]interface{}, error) {
	hits, ok := (*result)["hits"].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("unexpected response format: 'hits' key missing")
	}

	sourceArr, ok := hits["hits"].([]interface{})
	if !ok {
		return nil, fmt.Errorf("could not extract result array")
	}

	return sourceArr, nil
}
