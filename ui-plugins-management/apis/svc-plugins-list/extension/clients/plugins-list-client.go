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
	"context"
	logger "svc-plugins-list-layer/logging"
	"svc-plugins-list-layer/models"
)

type PluginsListClient struct {
	dynamodbClient *DynamodbClient     `json:"dynamodbClient,omitempty"`
	rdsClient      *RdsClient          `json:"rdsClient,omitempty"`
	cacheClient    *PluginsCacheClient `json:"cacheClient,omitempty"`
	log            *logger.Logger      `json:"logger,omitempty"`
}

func NewPluginsListClient(configuration *Configuration, log *logger.Logger) *PluginsListClient {
	return &PluginsListClient{
		dynamodbClient: NewDynamoDBClient(configuration),
		rdsClient:      NewRdsClient(configuration),
		cacheClient:    NewCacheClient(),
		log:            log,
	}
}

func (c *PluginsListClient) GetPlugins(ctx context.Context, tenantId string) (*models.Plugins, error) {
	c.log.Info("Getting Plugins List")
	pluginsListCache, err := c.cacheClient.GetPlugins(tenantId)
	if err != nil {
		return nil, err
	}
	if pluginsListCache != nil {
		c.log.Info("Got Plugins List from cache")
		return pluginsListCache, nil
	}

	// Cache is missed, populate the plugins list
	c.log.Info("Populating Plugins List")
	// Get plugin feature flags
	pluginFeatureFlags, err := c.dynamodbClient.GetPluginsFeatureFlags(ctx, tenantId)
	if err != nil {
		return nil, err
	}

	// Get plugins list
	pluginsList, err := c.rdsClient.GetPlugins(ctx, tenantId)
	if err != nil {
		return nil, err
	}

	// Assemble plugins list and set it in cache
	plugins, err := AssemblePluginsList(pluginsList, *pluginFeatureFlags)
	if err != nil {
		return nil, err
	}
	c.cacheClient.SetPlugins(tenantId, plugins)

	return plugins, nil
}

func AssemblePluginsList(plugins []models.Plugin, flags models.PluginsFeatures) (*models.Plugins, error) {
	var inboundPlugins []models.Plugin
	var outboundPlugins []models.Plugin
	for i := range plugins {
		plugin := &plugins[i]
		plugin.Flags = flags[plugin.Source]

		if plugin.IsInbound {
			inboundPlugins = append(inboundPlugins, *plugin)
		} else {
			outboundPlugins = append(outboundPlugins, *plugin)
		}
	}

	return &models.Plugins{
		Inbound:  inboundPlugins,
		Outbound: outboundPlugins,
	}, nil
}
