package clients

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"

	elasticsearch "github.com/elastic/go-elasticsearch/v7"
)

func FetchAssetDetails(ctx context.Context, ag string, targetType string, assetId string, size int) (*map[string]interface{}, error) {

	query := buildQuery(targetType, assetId)
	esRequest := map[string]interface{}{
		"size":  size,
		"query": query,
	}

	var buffer bytes.Buffer
	json.NewEncoder(&buffer).Encode(esRequest)
	client, _ := elasticsearch.NewDefaultClient()
	response, _ := client.Search(client.Search.WithIndex(ag), client.Search.WithBody(&buffer))
	if response.StatusCode != 200 {
		return nil, fmt.Errorf("error while fetching asset detials from ES for assetId: %s", assetId)
	}
	var result map[string]interface{}
	json.NewDecoder(response.Body).Decode(&result)
	return &result, nil
}

func buildQuery(targetType string, assetId string) map[string]interface{} {

	assetIdFilter := map[string]interface{}{
		"term": map[string]interface{}{
			docIDKeyword: assetId,
		},
	}
	targetTypeFilter := map[string]interface{}{
		"term": map[string]interface{}{
			docTypeKeyword: targetType,
		},
	}
	query := map[string]interface{}{
		"bool": map[string]interface{}{
			"must": [2]map[string]interface{}{assetIdFilter, targetTypeFilter},
		},
	}
	return query
}
