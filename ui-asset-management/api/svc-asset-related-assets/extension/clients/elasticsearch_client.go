package clients

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"svc-asset-related-assets-layer/models"
	"sync"

	"github.com/elastic/go-elasticsearch/v7"
)

type ElasticSearchClient struct {
	dynamodbClient           *DynamodbClient
	elasticsearchClientCache sync.Map
}

func NewElasticSearchClient(dynamodbClient *DynamodbClient) *ElasticSearchClient {
	log.Println("initialized opensearch client")
	return &ElasticSearchClient{
		dynamodbClient: dynamodbClient,
	}
}

func (c *ElasticSearchClient) CreateNewElasticSearchClient(ctx context.Context, tenantId string) (*elasticsearch.Client, error) {
	// Check if the client is already in the cache
	if client, ok := c.elasticsearchClientCache.Load(tenantId); ok {
		return client.(*elasticsearch.Client), nil // Type assert the value to *elasticsearch.Client
	}

	// If not found, proceed to create a new client
	esDomainProperties, err := c.dynamodbClient.GetOpenSearchDomain(ctx, tenantId)
	if err != nil {
		return nil, fmt.Errorf("error getting opensearch domain properties for tenant id [%s] %w", tenantId, err)
	}

	client, err := elasticsearch.NewClient(elasticsearch.Config{Addresses: []string{"https://" + esDomainProperties.Endpoint}})
	if err != nil {
		return nil, fmt.Errorf("error creating opensearch client for tenant id [%s] %w", tenantId, err)
	}

	// Store the new client in the cache
	c.elasticsearchClientCache.Store(tenantId, client)
	return client, nil
}

const (
	docTypeKeyword        = "docType.keyword"
	instanceIdKeyword     = "instanceid.keyword"
	resourceIdKeyword     = "_resourceid.keyword"
	_resourceId           = "_resourceid"
	docId                 = "_docid"
	targetTypeDisplayName = "targettypedisplayname"
)

func (c *ElasticSearchClient) FetchAssetDetails(ctx context.Context, tenantId, ag, assetId string) (*map[string]interface{}, error) {

	query := buildDetailsQuery(assetId)
	esRequest := map[string]interface{}{
		"size":  1,
		"query": query,
	}

	var buffer bytes.Buffer
	json.NewEncoder(&buffer).Encode(esRequest)

	client, err := c.CreateNewElasticSearchClient(ctx, tenantId)
	if err != nil {
		return nil, fmt.Errorf("error creating opensearch client for tenant id [%s] %w", tenantId, err)
	}
	response, err := client.Search(client.Search.WithIndex(ag), client.Search.WithBody(&buffer))

	if err != nil {
		return nil, fmt.Errorf("error getting response from ES for assetId: %s. err: %s", assetId, err)
	}
	defer response.Body.Close()

	if response.StatusCode != 200 {
		return nil, fmt.Errorf("error while fetching asset details from ES for assetId: %s", assetId)
	}
	var result map[string]interface{}
	json.NewDecoder(response.Body).Decode(&result)
	return &result, nil
}

func (c *ElasticSearchClient) FetchRelatedAssets(ctx context.Context, docTypes []string, tenantId, ag, resourceId string) (*map[string]interface{}, error) {

	var esRequest string
	for _, docType := range docTypes {
		relatedAssetQuery := buildRelatedAssetsQuery(docType, resourceId)
		searchQuery := map[string]interface{}{
			"size":  1000,
			"query": relatedAssetQuery,
		}
		jsonBytes, err := json.Marshal(searchQuery)
		if err != nil {
			return nil, fmt.Errorf("error marshalling related asset query %w", err)
		}
		esRequest += "{}\n" + string(jsonBytes) + "\n"
	}

	return c.fetchFromOpensearch(ctx, tenantId, ag, esRequest)
}

func (c *ElasticSearchClient) FetchMultipleAssetsByResourceId(ctx context.Context, tenantId, ag string, relatedAssets []models.RelatedAsset) (*map[string]interface{}, error) {

	var esRequest string
	for _, relatedAsset := range relatedAssets {
		assetDetailsQuery := buildAssetsQuery(relatedAsset.AssetType, relatedAsset.ResourceId)
		searchQuery := map[string]interface{}{
			"size":    1,
			"query":   assetDetailsQuery,
			"_source": []string{docId, targetTypeDisplayName, docType, _resourceId},
		}
		jsonBytes, err := json.Marshal(searchQuery)
		if err != nil {
			return nil, fmt.Errorf("error marshalling related asset query %w", err)
		}
		esRequest += "{}\n" + string(jsonBytes) + "\n"
	}

	return c.fetchFromOpensearch(ctx, tenantId, ag, esRequest)
}

func (c *ElasticSearchClient) fetchFromOpensearch(ctx context.Context, tenantId, esIndex, esQuery string) (*map[string]interface{}, error) {
	var buffer bytes.Buffer
	json.NewEncoder(&buffer).Encode(esQuery)

	client, err := c.CreateNewElasticSearchClient(ctx, tenantId)
	if err != nil {
		return nil, fmt.Errorf("error creating opensearch client for tenant id [%s] %w", tenantId, err)
	}

	response, err := client.Msearch(bytes.NewReader([]byte(esQuery)), client.Msearch.WithIndex(esIndex))
	if err != nil {
		return nil, fmt.Errorf("error getting related assets response from Opensearch. err: %s", err)
	}
	defer response.Body.Close()

	if response.StatusCode != 200 {
		return nil, fmt.Errorf("error getting related assets response from Opensearch. err: %s", err)
	}

	var result map[string]interface{}
	json.NewDecoder(response.Body).Decode(&result)
	return &result, nil
}

func buildDetailsQuery(assetId string) map[string]interface{} {
	assetIdFilter := map[string]interface{}{
		"term": map[string]interface{}{
			"_id": assetId,
		},
	}

	query := map[string]interface{}{
		"bool": map[string]interface{}{
			"must": [1]map[string]interface{}{assetIdFilter},
		},
	}

	return query
}

func buildRelatedAssetsQuery(docType, resourceId string) map[string]interface{} {
	docTypeFilter := map[string]interface{}{
		"term": map[string]interface{}{
			docTypeKeyword: docType,
		},
	}

	resourceIdFilter := map[string]interface{}{
		"term": map[string]interface{}{
			instanceIdKeyword: resourceId,
		},
	}

	query := map[string]interface{}{
		"bool": map[string]interface{}{
			"must": [2]map[string]interface{}{docTypeFilter, resourceIdFilter},
		},
	}

	return query
}

func buildAssetsQuery(docType, resourceId string) map[string]interface{} {
	docTypeFilter := map[string]interface{}{
		"term": map[string]interface{}{
			docTypeKeyword: docType,
		},
	}

	resourceIdFilter := map[string]interface{}{
		"term": map[string]interface{}{
			resourceIdKeyword: resourceId,
		},
	}

	query := map[string]interface{}{
		"bool": map[string]interface{}{
			"must": [2]map[string]interface{}{docTypeFilter, resourceIdFilter},
		},
	}

	return query
}
