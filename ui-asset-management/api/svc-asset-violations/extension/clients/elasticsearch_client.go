package clients

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"svc-asset-violations-layer/models"

	elasticsearch "github.com/elastic/go-elasticsearch/v7"
)

type ElasticSearchClient struct {
}

func NewElasticSearchClient() *ElasticSearchClient {
	return &ElasticSearchClient{}
}

func (c *ElasticSearchClient) FetchAssetViolations(ctx context.Context, esDomainProperties *models.EsDomainProperties, ag string, assetId string) (*map[string]interface{}, error) {

	query := buildQuery(assetId)
	fmt.Println("query: ", query)
	esRequest := map[string]interface{}{
		"size":    1000,
		"query":   query,
		"_source": [3]string{"policyId", "issueStatus", "_id"},
	}

	var buffer bytes.Buffer
	json.NewEncoder(&buffer).Encode(esRequest)
	fmt.Println("esRequest: ", string(buffer.Bytes()))

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

	query := map[string]interface{}{
		"bool": map[string]interface{}{
			"must": [3]map[string]interface{}{buildTermQuery("_docid.keyword", assetId), buildTermQuery("type", "issue"), buildTermQuery("issueStatus", []string{"open", "exempted"})},
		},
	}
	return query
}

func buildTermQuery(key string, value interface{}) map[string]interface{} {
	termKey := "term"
	if _, ok := value.([]string); ok {
		termKey = "terms"
	}

	return map[string]interface{}{
		termKey: map[string]interface{}{
			key: value,
		},
	}
}
