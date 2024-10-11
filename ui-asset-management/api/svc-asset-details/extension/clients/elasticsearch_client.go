package clients

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"github.com/elastic/go-elasticsearch/v7"
)

type ElasticSearchClient struct {
	dynamodbClient           *DynamodbClient
	elasticsearchClientCache map[string]*elasticsearch.Client
}

func NewElasticSearchClient(dynamodbClient *DynamodbClient) *ElasticSearchClient {
	fmt.Println("Initialized Elastic Search Client")
	return &ElasticSearchClient{
		dynamodbClient:           dynamodbClient,
		elasticsearchClientCache: make(map[string]*elasticsearch.Client),
	}
}

func (c *ElasticSearchClient) CreateNewElasticSearchClient(ctx context.Context, tenantId string) (*elasticsearch.Client, error) {
	// check if the client is already created
	if client, ok := c.elasticsearchClientCache[tenantId]; ok {
		return client, nil
	}

	esDomainProperties, err := c.dynamodbClient.GetOpenSearchDomain(ctx, tenantId)
	if err != nil {
		return nil, err
	}

	client, err := elasticsearch.NewClient(elasticsearch.Config{Addresses: []string{"https://" + esDomainProperties.Endpoint}})
	if err != nil {
		return nil, fmt.Errorf("error creating ES client for tenantId: %s. err: %s", tenantId, err)
	}

	c.elasticsearchClientCache[tenantId] = client
	return client, nil
}

func (c *ElasticSearchClient) FetchAssetDetails(ctx context.Context, tenantId, ag, assetId string, size int) (*map[string]interface{}, error) {
	query := buildQuery(assetId)
	esRequest := map[string]interface{}{
		"size":  size,
		"query": query,
	}

	var buffer bytes.Buffer
	json.NewEncoder(&buffer).Encode(esRequest)

	client, err := c.CreateNewElasticSearchClient(ctx, tenantId)
	if err != nil {
		return nil, err
	}

	response, err := client.Search(client.Search.WithIndex(ag), client.Search.WithBody(&buffer))

	if err != nil {
		return nil, fmt.Errorf("error getting response from ES for assetId: %s. err: %s", assetId, err)
	}
	defer response.Body.Close()

	if response.StatusCode != 200 {
		return nil, fmt.Errorf("error while fetching asset detials from ES for assetId: %s", assetId)
	}
	var result map[string]interface{}
	json.NewDecoder(response.Body).Decode(&result)
	return &result, nil
}

func buildQuery(assetId string) map[string]interface{} {

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
