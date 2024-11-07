package clients

import (
	"context"
	"fmt"
	"log"
	"strings"
	"svc-asset-opinions-layer/models"
)

type AssetOpinionsClient struct {
	elasticSearchClient *ElasticSearchClient
}

func NewAssetOpinionsClient(ctx context.Context, config *Configuration) (*AssetOpinionsClient, error) {
	dynamodbClient, err := NewDynamoDBClient(ctx, config.UseAssumeRole, config.AssumeRoleArn, config.Region, config.TenantConfigOutputTable, config.TenantTablePartitionKey)
	if err != nil {
		return nil, fmt.Errorf("error creating dynamodb client %w", err)
	}

	opensearchClient := NewElasticSearchClient(dynamodbClient)
	return &AssetOpinionsClient{
		elasticSearchClient: opensearchClient,
	}, nil
}

const (
	docId    = "_docId"
	docType  = "_docType"
	opinions = "opinions"
	success  = "success"
)

func (c *AssetOpinionsClient) GetAssetOpinions(ctx context.Context, tenantId, source, targetType, assetId string) (*models.Response, error) {

	if len(strings.TrimSpace(assetId)) == 0 {
		return nil, fmt.Errorf("asset id must be present")
	}

	if len(strings.TrimSpace(source)) == 0 {
		return nil, fmt.Errorf("source must be present")
	}
	if len(strings.TrimSpace(targetType)) == 0 {
		return nil, fmt.Errorf("targetType must be present")
	}

	log.Printf("starting to fetch opinions for asset id [%s] and tenant id [%s]\n", assetId, tenantId)

	result, err := c.elasticSearchClient.FetchAssetOpinions(ctx, tenantId, source, targetType, assetId)
	if err != nil {

		return nil, fmt.Errorf("error fetching asset Opinions %w", err)
	}

	assetOpinions, err := extractSourceFromResult(result, assetId)
	if err != nil {
		return nil, fmt.Errorf("failed to extract source from result: %w", err)
	}

	allOpinions := models.OpinionsResponse{}
	if v, ok := assetOpinions[docId]; ok {
		allOpinions.DocId = v.(string)
	}
	if v, ok := assetOpinions[docType]; ok {
		allOpinions.DocType = v.(string)
	}

	if v, ok := assetOpinions[opinions]; ok {
		allOpinions.Opinions = v.(map[string]interface{})
	}

	return &models.Response{Data: &allOpinions, Message: "success"}, nil
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
