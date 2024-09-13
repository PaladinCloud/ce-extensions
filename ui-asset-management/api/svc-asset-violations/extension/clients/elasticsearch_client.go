package clients

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"

	elasticsearch "github.com/elastic/go-elasticsearch/v7"
)

type ElasticSearchClient struct {
}

func NewElasticSearchClient() *ElasticSearchClient {
	return &ElasticSearchClient{}
}

func (c *ElasticSearchClient) FetchAssetViolations(ctx context.Context, ag string, assetId string) (*map[string]interface{}, error) {

	query := buildQuery(assetId)
	esRequest := map[string]interface{}{
		"size":    500,
		"query":   query,
		"_source": [2]string{"policyId", "issueStatus"},
	}

	var buffer bytes.Buffer
	json.NewEncoder(&buffer).Encode(esRequest)

	client, _ := elasticsearch.NewDefaultClient()
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
			"must": [3]map[string]interface{}{buildTermQuery("_docid.keyword", assetId), buildTermQuery("type", "issue"), buildTermQuery("issueStatus", [2]string{"open", "exempted"})},
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
