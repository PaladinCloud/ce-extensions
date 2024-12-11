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

const (
	opinionIndexFormat = "%s_%s_%s"
)

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

func (c *ElasticSearchClient) FetchAssetOpinions(ctx context.Context, tenantId, source, targetType, assetId string) (*map[string]interface{}, error) {

	query := buildOpinionsQuery(assetId)
	esRequest := map[string]interface{}{
		"size":  1,
		"query": query,
	}

	log.Printf("fetching opinions for assetId: %s", assetId)

	var buffer bytes.Buffer
	json.NewEncoder(&buffer).Encode(esRequest)

	client, err := c.CreateNewElasticSearchClient(ctx, tenantId)
	if err != nil {
		return nil, fmt.Errorf("error creating opensearch client for tenant id [%s] %w", tenantId, err)
	}
	opinionIndex := fmt.Sprintf(opinionIndexFormat, source, targetType, opinions)
	response, err := client.Search(client.Search.WithContext(ctx), client.Search.WithIndex(opinionIndex), client.Search.WithBody(&buffer))

	if err != nil {
		return nil, fmt.Errorf("error getting opinions from opensearch client for asset id [%s] %w", assetId, err)
	}
	defer response.Body.Close()

	if response.StatusCode != 200 {
		return nil, fmt.Errorf("error while fetching opinions from opensearch client for asset id [%s]", assetId)
	}
	var result map[string]interface{}
	json.NewDecoder(response.Body).Decode(&result)
	return &result, nil
}

func buildOpinionsQuery(assetId string) map[string]interface{} {
	assetIdFilter := buildDocIdFilter(assetId)

	query := map[string]interface{}{
		"bool": map[string]interface{}{
			"must": [1]map[string]interface{}{assetIdFilter},
		},
	}

	return query
}

func buildDocIdFilter(docId string) map[string]interface{} {

	docIdFilter1 := map[string]interface{}{
		"term": map[string]interface{}{
			"_docid.keyword": docId,
		},
	}
	docIdFilter2 := map[string]interface{}{
		"term": map[string]interface{}{
			"_docId.keyword": docId,
		},
	}

	docIdOrFilter := map[string]interface{}{
		"bool": map[string]interface{}{
			"should": []interface{}{docIdFilter1, docIdFilter2},
		},
	}
	return docIdOrFilter
}
