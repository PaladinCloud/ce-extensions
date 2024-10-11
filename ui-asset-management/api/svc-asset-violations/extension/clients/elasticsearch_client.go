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

func (c *ElasticSearchClient) FetchAssetViolations(ctx context.Context, tenantId, ag, assetId string) (*map[string]interface{}, error) {

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
