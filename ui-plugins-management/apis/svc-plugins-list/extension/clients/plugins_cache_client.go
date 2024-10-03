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
	"svc-plugins-list-layer/models"
	"sync"
)

type PluginsCacheClient struct {
	cache map[string]*models.Plugins // Store a list of plugins in a cache
	mu    sync.RWMutex
}

// NewCacheClient initializes a PluginsCacheClient with an empty map
func NewCacheClient() *PluginsCacheClient {
	println("Initialized Cache Client")
	return &PluginsCacheClient{
		cache: make(map[string]*models.Plugins),
	}
}

// GetPlugins retrieves a plugin from the cache by its key
func (c *PluginsCacheClient) GetPlugins(key string) (*models.Plugins, error) {
	c.mu.RLock()
	defer c.mu.RUnlock() // Ensure the lock is released after the operation

	plugins, exists := c.cache[key]
	if !exists {
		println("Key not found in plugins cache")
		return nil, nil
	}
	return plugins, nil
}

// SetPlugins adds or updates a plugin in the cache
func (c *PluginsCacheClient) SetPlugins(key string, value *models.Plugins) (*models.Plugins, error) {
	println("Setting plugin in cache")

	c.mu.Lock()         // Use Write lock since we are modifying the cache
	defer c.mu.Unlock() // Ensure the lock is released after the operation

	c.cache[key] = value
	return value, nil
}
