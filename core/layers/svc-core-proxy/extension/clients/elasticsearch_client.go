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
	"sync"

	"github.com/elastic/go-elasticsearch/v7"
)

type ElasticSearchClient struct {
	dynamodbClient           *DynamodbClient
	elasticsearchClientCache sync.Map
}

func NewElasticSearchClient(dynamodbClient *DynamodbClient) *ElasticSearchClient {
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

	client, err := elasticsearch.NewClient(elasticsearch.Config{Addresses: []string{"https://" + esDomainProperties.EsDomain.Endpoint}})
	if err != nil {
		return nil, fmt.Errorf("error creating opensearch client for tenant id [%s] %w", tenantId, err)
	}

	// Store the new client in the cache
	c.elasticsearchClientCache.Store(tenantId, client)
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
	if err := json.NewDecoder(response.Body).Decode(&result); err != nil {
		return nil, fmt.Errorf("failed to decode response body for asset [%s]: %w", assetId, err)
	}
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
