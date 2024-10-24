package clients

import (
	"context"
	"fmt"
	"os"
	"slices"
	"strings"
	"svc-asset-related-assets-layer/models"
)

type RelatedAssetsClient struct {
	elasticSearchClient *ElasticSearchClient
}

func NewRelatedAssetsClient(ctx context.Context, config *Configuration) *RelatedAssetsClient {
	dynamodbClient, _ := NewDynamoDBClient(ctx, config.AssumeRoleArn, config.Region, config.TenantConfigOutputTable, config.TenantTablePartitionKey)
	return &RelatedAssetsClient{elasticSearchClient: NewElasticSearchClient(dynamodbClient)}
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
)

var (
	defaultTargetTypeWithRelatedAssets = [1]string{"ec2"}
	targetTypeWithRelatedAssets        = os.Getenv(targetTypeRelatedAssetsEnv)
	relatedAssetDoctypes               = map[string][]string{"ec2": []string{doctypeSecGroup, doctypeBlockDevices}}
)

func (c *RelatedAssetsClient) GetRelatedAssetsDetails(ctx context.Context, tenantId, targetType, assetId string) (*models.Response, error) {

	if len(strings.TrimSpace(assetId)) == 0 {
		return nil, fmt.Errorf("assetId must be present")
	}

	var validTargetTypes []string
	if targetTypeWithRelatedAssets == "" {
		validTargetTypes = defaultTargetTypeWithRelatedAssets[:]
	} else {
		validTargetTypes = strings.Split(targetTypeWithRelatedAssets, ",")
	}
	if !slices.Contains(validTargetTypes, targetType) {
		fmt.Println("No valid related assets for target type: %s" + targetType)
		return &models.Response{Data: &models.RelatedAssets{AllRelatedAssets: &[]models.RelatedAsset{}}, Message: success}, nil
	}

	// related assets like Public Ip and Iam instance profile ARN are present in the asset details doc
	fmt.Println("Fetching asset details")
	result, err := c.elasticSearchClient.FetchAssetDetails(ctx, tenantId, allSources, assetId)
	if err != nil {
		return nil, err
	}

	assetDetails := getResults(result)
	if len(assetDetails) == 0 {
		return nil, fmt.Errorf("asset detials not found for asset id: %s", assetId)
	}

	allRelatedAssets := []models.RelatedAsset{}
	assetDetail := assetDetails[0].(map[string]interface{})["_source"].(map[string]interface{})

	if v, ok := assetDetail[instanceid]; ok {

		// fetch related assets for the asset
		fmt.Println("Fetching related assets")
		esResponse, err := c.elasticSearchClient.FetchRelatedAssets(ctx, relatedAssetDoctypes[targetType], tenantId, allSources, v.(string))
		if err != nil {
			return nil, err
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

		// fetch assetId and other info for the related assets from the related assets' own details
		fmt.Println("Fetching asset details of related assets")
		relatedAssetDetails, err := c.elasticSearchClient.FetchMultipleAssetsByResourceId(ctx, tenantId, allSources, allRelatedAssets)
		if err != nil {
			return nil, err
		}

		for _, response := range (*relatedAssetDetails)["responses"].([]interface{}) {
			responseDetailMap := response.(map[string]interface{})
			relatedAssetArray := getResults(&responseDetailMap)
			for _, relatedAssetDoc := range relatedAssetArray {
				relatedAssetDetails := relatedAssetDoc.(map[string]interface{})["_source"].(map[string]interface{})
				assetTypeName := relatedAssetDetails[targetTypeDisplayName].(string)
				docId := relatedAssetDetails[docId].(string)
				assetType := relatedAssetDetails[docType].(string)
				resourceId := relatedAssetDetails[_resourceId].(string)
				for i, relatedAsset := range allRelatedAssets {
					if relatedAsset.AssetType == assetType && relatedAsset.ResourceId == resourceId {
						allRelatedAssets[i].AssetId = docId
						allRelatedAssets[i].AssetTypeName = assetTypeName
					}
				}
			}
		}

		if v, ok := assetDetail[publicIpAddress]; ok && v != "" {
			publicIpAsset := models.RelatedAsset{ResourceId: v.(string), AssetTypeName: publicIpAddressDisplayName}
			allRelatedAssets = append(allRelatedAssets, publicIpAsset)
		}
		if v, ok := assetDetail[iamInstanceProfileArn]; ok && v != "" {
			iamInstanceProfileArnAsset := models.RelatedAsset{ResourceId: v.(string), AssetTypeName: iamInstanceProfileArnDisplayName}
			allRelatedAssets = append(allRelatedAssets, iamInstanceProfileArnAsset)
		}

	} else {
		return nil, fmt.Errorf("instance Id not found for assetId: %s", assetId)
	}
	return &models.Response{Data: &models.RelatedAssets{AllRelatedAssets: &allRelatedAssets}, Message: success}, nil
}

func getResults(esResponse *map[string]interface{}) []interface{} {
	return (*esResponse)["hits"].(map[string]interface{})["hits"].([]interface{})
}
