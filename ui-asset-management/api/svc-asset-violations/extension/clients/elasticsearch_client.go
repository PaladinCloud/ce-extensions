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

func (c *ElasticSearchClient) FetchAssetViolationsWithAggregations(ctx context.Context, tenantId, ag, assetId string, policyIds []string) (*models.ViolationsOpenSearchResult, error) {
	query := buildQueryWithAggregations(assetId, policyIds)
	log.Printf("Query: %+v\n", query)

	esRequest := map[string]interface{}{
		"size":    100,
		"query":   query["query"],
		"aggs":    query["aggs"],
		"_source": []string{"_id", "policyId", "issueStatus"},
	}

	var buffer bytes.Buffer
	err := json.NewEncoder(&buffer).Encode(esRequest)
	if err != nil {
		return nil, fmt.Errorf("failed to encode opensearch client request %w", err)
	}

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
		return nil, fmt.Errorf("opensearch request failed with status %d for asset id [%s] %s", response.StatusCode, assetId, response.Body)
	}

	var result models.ViolationsOpenSearchResult
	err = json.NewDecoder(response.Body).Decode(&result)
	if err != nil {
		return nil, fmt.Errorf("error decoding response body %w", err)
	}

	return &result, nil
}

func buildQueryWithAggregations(assetId string, policyIds []string) map[string]interface{} {
	query := map[string]interface{}{
		"query": map[string]interface{}{
			"bool": map[string]interface{}{
				"must": []map[string]interface{}{
					{
						"term": map[string]interface{}{
							"_docid.keyword": map[string]string{
								"value": assetId,
							},
						},
					},
					{
						"term": map[string]interface{}{
							"type.keyword": map[string]string{
								"value": "issue",
							},
						},
					},
					{
						"terms": map[string]interface{}{
							"issueStatus.keyword": []string{"open", "exempted"},
						},
					},
					{
						"terms": map[string]interface{}{
							"policyId.keyword": policyIds,
						},
					},
				},
			},
		},
		"aggs": map[string]interface{}{
			"Severity": map[string]interface{}{
				"terms": map[string]interface{}{
					"field": "severity.keyword",
					"size":  4,
				},
			},
		},
	}

	return query
}
