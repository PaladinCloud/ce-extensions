package clients

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
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
	docIDKeyword   = "_docid.keyword"
	docTypeKeyword = "docType.keyword"
)

var (
	assetPortRulesDoctype = map[string]string{"sg": "sg_rules"}
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
		return nil, fmt.Errorf("error getting response from opensearch client for asset id [%s] %w", assetId, err)
	}
	defer response.Body.Close()

	if response.StatusCode != 200 {
		return nil, fmt.Errorf("error while fetching asset details from opensearch client for asset id [%s]", assetId)
	}
	var result map[string]interface{}
	json.NewDecoder(response.Body).Decode(&result)
	return &result, nil
}

func (c *ElasticSearchClient) FetchChildResourcesDetails(ctx context.Context, tenantId, ag, targetType string, assetId string) (*map[string]interface{}, error) {
	query := buildChildResourcesQuery(targetType, assetId)
	esRequest := map[string]interface{}{
		"size":  10000,
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
		return nil, fmt.Errorf("error getting response from opensearch client for asset id [%s] %w", assetId, err)
	}
	defer response.Body.Close()

	if response.StatusCode != 200 {
		return nil, fmt.Errorf("error while fetching asset details from opensearch client for asset id [%s]", assetId)
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

func buildChildResourcesQuery(targetType string, assetId string) map[string]interface{} {

	docTypeFilter := map[string]interface{}{
		"term": map[string]interface{}{
			docTypeKeyword: assetPortRulesDoctype[targetType],
		},
	}

	childrenFilter := map[string]interface{}{
		"has_parent": map[string]interface{}{
			"query": map[string]interface{}{
				"term": map[string]interface{}{
					docIDKeyword: assetId,
				},
			},
			"parent_type": targetType,
		},
	}

	query := map[string]interface{}{
		"bool": map[string]interface{}{
			"must": [2]map[string]interface{}{docTypeFilter, childrenFilter},
		},
	}

	return query
}
