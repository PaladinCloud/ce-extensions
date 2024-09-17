package clients

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"svc-asset-details-layer/models"

	elasticsearch "github.com/elastic/go-elasticsearch/v7"
)

type ElasticSearchClient struct {
}

func NewElasticSearchClient() *ElasticSearchClient {
	return &ElasticSearchClient{}
}

func (c *ElasticSearchClient) FetchAssetDetails(ctx context.Context, esDomainProperties *models.EsDomainProperties, ag string, assetId string, size int) (*map[string]interface{}, error) {

	query := buildQuery(assetId)
	esRequest := map[string]interface{}{
		"size":  size,
		"query": query,
	}

	var buffer bytes.Buffer
	json.NewEncoder(&buffer).Encode(esRequest)

	client, _ := elasticsearch.NewClient(elasticsearch.Config{Addresses: []string{"https://" + esDomainProperties.Endpoint}})
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
