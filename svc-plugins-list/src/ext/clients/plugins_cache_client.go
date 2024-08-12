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
	"sync"
)

type PluginsCacheClient struct {
	cache *models.Plugins
	mu    sync.RWMutex
}

// NewCacheClient  inits a DynamoDB session to be used throughout the services
func NewCacheClient() *PluginsCacheClient {
	println("Creating new Cache Client")
	return &PluginsCacheClient{
		cache: nil,
	}
}

func (c *PluginsCacheClient) GetPluginsCache() (*models.Plugins, error) {
	c.mu.RLock()
	if c.cache == nil {
		println("Key not found in plugins cache")
		return nil, nil
	}
	c.mu.RUnlock()

	return c.cache, nil
}

func (c *PluginsCacheClient) SetPluginsCache(value *models.Plugins) (*models.Plugins, error) {
	println("Setting plugins cache")

	c.mu.RLock()
	c.cache = value
	c.mu.RUnlock()

	return value, nil
}
