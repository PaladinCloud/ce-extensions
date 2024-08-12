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
	"cache-layer/src/ext/models"
	"context"
)

type PluginsListClient struct {
	dynamodbClient *DynamodbClient
	rdsClient      *RdsClient
	cacheClient    *PluginsCacheClient
}

const (
	cacheKey = "plugins"
)

func NewPluginsListClient(configuration *Configuration) *PluginsListClient {
	return &PluginsListClient{
		dynamodbClient: NewDynamoDBClient(configuration),
		rdsClient:      NewRdsClient(configuration),
		cacheClient:    NewCacheClient(),
	}
}

func (c *PluginsListClient) GetPluginsList(ctx context.Context) (*models.Plugins, error) {
	println("Getting Plugins List")
	plugins, err := c.cacheClient.GetPluginsCache()
	if err != nil {
		return nil, err
	}
	if plugins != nil {
		println("Got Plugins List from cache")
		return plugins, nil
	}

	println("Populating Plugins List")
	// Get plugin feature flags
	pluginFeatureFlags, err := c.dynamodbClient.GetPluginFeatureFlags(ctx)
	if err != nil {
		return nil, err
	}

	// Get plugins list and join with feature flags
	plugins, err = c.rdsClient.GetPluginsList(ctx, *pluginFeatureFlags)
	if err != nil {
		return nil, err
	}

	c.cacheClient.SetPluginsCache(plugins)

	return plugins, nil
}
