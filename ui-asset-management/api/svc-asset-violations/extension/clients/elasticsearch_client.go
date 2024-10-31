/*
 * Copyright (c) 2024 Paladin Cloud, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package clients

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"svc-asset-violations-layer/models"
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

func (c *ElasticSearchClient) FetchAssetViolations(ctx context.Context, tenantId, ag, assetId string) (*models.PolicyViolationsMap, error) {
	query := buildQuery(assetId)
	log.Printf("Query: %+v\n", query)

	esRequest := map[string]interface{}{
		"size":    1000,
		"query":   query,
		"_source": [3]string{"policyId", "issueStatus", "_id"},
	}

	var buffer bytes.Buffer
	err := json.NewEncoder(&buffer).Encode(esRequest)
	if err != nil {
		return nil, fmt.Errorf("failed to encode opensearch client request %w", err)
	}
	log.Printf("opensearch client request: %s\n", buffer.String())

	client, err := c.CreateNewElasticSearchClient(ctx, tenantId)
	if err != nil {
		return nil, fmt.Errorf("error creating opensearch client for tenant id [%s] %w", tenantId, err)
	}
	response, err := client.Search(client.Search.WithIndex(ag), client.Search.WithBody(&buffer))

	if err != nil {
		return nil, fmt.Errorf("error getting response from opensearch client for asset id: [%s] %w", assetId, err)
	}
	defer response.Body.Close()

	if response.StatusCode != 200 {
		return nil, fmt.Errorf("error while fetching asset details from opensearch client for asset id [%s]", assetId)
	}

	var result map[string]interface{}
	err = json.NewDecoder(response.Body).Decode(&result)
	if err != nil {
		return nil, fmt.Errorf("error decoding response body %w", err)
	}

	hits, ok := result["hits"].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("unexpected result structure: missing or invalid 'hits'")
	}

	sourceArr, ok := hits["hits"].([]interface{})
	if !ok {
		return nil, fmt.Errorf("unexpected result structure: missing or invalid 'hits.hits'")
	}

	var policyViolations = models.PolicyViolationsMap{}
	if len(sourceArr) > 0 {
		log.Printf("found [%d] violations for asset id [%s]\n", len(sourceArr), assetId)

		policyViolations.PolicyViolationsMap = make(map[string]interface{}, len(sourceArr))
		// Put the violations in map with policyId as key
		for i := 0; i < len(sourceArr); i++ {
			violationDetail := sourceArr[i].(map[string]interface{})["_source"].(map[string]interface{})
			violationDetail["_id"] = sourceArr[i].(map[string]interface{})["_id"]
			policyViolations.PolicyViolationsMap[violationDetail["policyId"].(string)] = violationDetail
		}
	}

	return &policyViolations, nil
}

func buildQuery(assetId string) map[string]interface{} {
	query := map[string]interface{}{
		"bool": map[string]interface{}{
			"must": [3]map[string]interface{}{
				buildTermQuery("_docid.keyword", assetId),
				buildTermQuery("type", "issue"),
				buildTermQuery("issueStatus", []string{"open", "exempted"}),
			},
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
