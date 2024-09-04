package clients

import (
	"context"
	"fmt"
	"strings"
	"svc-asset-details-layer/models"
)

type AssetDetailsClient struct {
	elasticSearchClient *ElasticSearchClient
}

func NewAssetDetailsClient() *AssetDetailsClient {
	return &AssetDetailsClient{elasticSearchClient: NewElasticSearchClient()}
}

const (
	docIDKeyword   = "_docid.keyword"
	docTypeKeyword = "docType.keyword"
)

func (c *AssetDetailsClient) GetAssetDetails(ctx context.Context, ag string, targetType string, assetId string) (*models.AssetDetails, error) {
	if len(strings.TrimSpace(assetId)) == 0 {
		return nil, fmt.Errorf("assetId must be present")
	}

	result, err := c.elasticSearchClient.FetchAssetDetails(ctx, ag, targetType, assetId, 1)

	if err != nil {
		return nil, err
	}

	sourceArr := (*result)["hits"].(map[string]interface{})["hits"].([]interface{})
	if len(sourceArr) > 0 {
		assetDetails := sourceArr[0].(map[string]interface{})
		return &models.AssetDetails{
			AccountId:       fmt.Sprintf("%v", assetDetails["accountId"]),
			Source:          assetDetails["source"].(string),
			SourceName:      assetDetails["sourceName"].(string),
			TargetType:      assetDetails["targetType"].(string),
			Tags:            assetDetails["tags"].(map[string]string),
			PrimaryProvider: fmt.Sprintf("%v", assetDetails["rawData"]),
		}, nil
	} else {
		return nil, fmt.Errorf("asset detials not found for assetId: %s", assetId)
	}
}
