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
	fmt.Println("initialized opensearch client")
	return &ElasticSearchClient{
		dynamodbClient: dynamodbClient,
	}
}

const (
	aggs              = "aggs"
	name              = "name"
	terms             = "terms"
	size              = "size"
	assetStateKeyword = "_assetState.keyword"
)

func (c *ElasticSearchClient) CreateNewElasticSearchClient(ctx context.Context, tenantId string) (*elasticsearch.Client, error) {
	// Check if the client is already in the cache
	if client, ok := c.elasticsearchClientCache.Load(tenantId); ok {
		return client.(*elasticsearch.Client), nil // Type assert the value to *elasticsearch.Client
	}

	// If not found, proceed to create a new client
	esDomainProperties, err := c.dynamodbClient.GetOpenSearchDomain(ctx, tenantId)
	if err != nil {
		return nil, err
	}

	client, err := elasticsearch.NewClient(elasticsearch.Config{Addresses: []string{"https://" + esDomainProperties.Endpoint}})
	if err != nil {
		return nil, fmt.Errorf("error creating opensearch client for tenant id: %s. err: %+v", tenantId, err)
	}

	// Store the new client in the cache
	c.elasticsearchClientCache.Store(tenantId, client)
	return client, nil
}

func (c *ElasticSearchClient) FetchAssetTypesForAssetGroup(ctx context.Context, tenantId, ag string) (*map[string]interface{}, error) {

	fmt.Printf("fetching target types for asset group: %s from opensearch", ag)

	client, err := c.CreateNewElasticSearchClient(ctx, tenantId)
	if err != nil {
		return nil, err
	}
	response, err := client.Indices.GetAlias(client.Indices.GetAlias.WithName(ag), client.Indices.GetAlias.WithContext(ctx))

	if err != nil {
		return nil, fmt.Errorf("error getting target types from opensearch client for asset group: %s. err: %+v", ag, err)
	}
	defer response.Body.Close()

	if response.StatusCode != 200 {
		return nil, fmt.Errorf("error getting target types from opensearch client for asset group: %s. err: %+v", ag, err)
	}

	var result map[string]interface{}
	err = json.NewDecoder(response.Body).Decode(&result)
	if err != nil {
		return nil, fmt.Errorf("error decoding response body: %+v", err)
	}

	return &result, nil
}

func (c *ElasticSearchClient) FetchAssetStateCount(ctx context.Context, tenantId, ag string, assetTypes []string) (*[]interface{}, error) {
	query := buildQuery(assetTypes)

	esRequest := map[string]interface{}{
		size:    0,
		"query": query,
		aggs:    buildAggregateQuery(assetStateKeyword),
	}

	var buffer bytes.Buffer
	err := json.NewEncoder(&buffer).Encode(esRequest)
	if err != nil {
		return nil, fmt.Errorf("failed to encode opensearch client request: %+v", err)
	}
	fmt.Printf("opensearch client request: %s\n", buffer.String())

	client, err := c.CreateNewElasticSearchClient(ctx, tenantId)
	if err != nil {
		return nil, err
	}
	response, err := client.Search(client.Search.WithContext(ctx), client.Search.WithIndex(ag), client.Search.WithBody(&buffer))

	if err != nil {
		return nil, fmt.Errorf("error getting response from opensearch client for asset group: %s. err: %+v", ag, err)
	}
	defer response.Body.Close()

	if response.StatusCode != 200 {
		return nil, fmt.Errorf("error while fetching asset details from opensearch client for asset group: %s", ag)
	}

	var result map[string]interface{}
	err = json.NewDecoder(response.Body).Decode(&result)
	if err != nil {
		return nil, fmt.Errorf("error decoding response body: %+v", err)
	}

	aggregations, ok := result["aggregations"].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("unexpected result structure: missing or invalid 'aggregations'")
	}
	name, ok := aggregations["name"].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("unexpected result structure: missing or invalid 'aggregations.name'")
	}
	buckets, ok := name["buckets"].([]interface{})
	if !ok {
		return nil, fmt.Errorf("unexpected result structure: missing or invalid 'aggregations.name.buckets'")
	}
	return &buckets, nil
}

func buildQuery(assetTypes []string) map[string]interface{} {
	query := map[string]interface{}{
		"bool": map[string]interface{}{
			"must": [3]map[string]interface{}{
				buildTermQuery("_entity", "true"),
				buildTermQuery("latest", "true"),
				buildTermQuery("_entitytype.keyword", assetTypes),
			},
		},
	}

	return query
}

func buildTermQuery(key string, value interface{}) map[string]interface{} {
	termKey := "term"
	if _, ok := value.([]string); ok {
		termKey = terms
	}

	return map[string]interface{}{
		termKey: map[string]interface{}{
			key: value,
		},
	}
}

func buildAggregateQuery(fieldName string) map[string]interface{} {
	query := map[string]interface{}{
		name: map[string]interface{}{
			terms: map[string]interface{}{
				"field": fieldName,
				size:    1000,
			},
		},
	}
	return query
}
