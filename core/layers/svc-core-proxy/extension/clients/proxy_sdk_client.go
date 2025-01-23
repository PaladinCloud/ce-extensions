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
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"svc-core-proxy-layer/models"
)

const (
	proxy_host = "localhost"
	proxy_port = "4568"
	base_url   = "https://" + proxy_host + ":" + proxy_host
)

// Helper func, given context and a url sends get request to that url
// expects to get a model.Response back from sever and returns that
func SendGetReq(ctx context.Context, url string) (*models.Response, error) {
	httpReq, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("error creating http request %w", err)
	}
	resp, err := http.DefaultClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("error making HTTP request: %w", err)
	}
	if !(resp.StatusCode > 199 && resp.StatusCode < 300) {
		return nil, fmt.Errorf("server erorr: %s", resp.Status)
	}
	var result models.Response
	decoder := json.NewDecoder(resp.Body)
	if err := decoder.Decode(&result); err != nil {
		return nil, fmt.Errorf("error decoding JSON response to struct: %w", err)
	}
	return &result, nil
}

// sends get request  for tenant secret to proxy
func getTenantSecret(ctx context.Context, tenantID string, id string) (*models.Response, error) {
	encodedTenantID := url.QueryEscape(tenantID)
	encodedID := url.QueryEscape(id)
	reqUrl := fmt.Sprintf("%s/tenant/%s/secrets/%s", base_url, encodedTenantID, encodedID)
	return DoGetReq(ctx, reqUrl)

}

// sends get request  for tenant features to proxy
func getTenantFeatures(ctx context.Context, tenantID string) (*models.Response, error) {
	encodedTenantID := url.QueryEscape(tenantID)
	reqUrl := fmt.Sprintf("%s/tenant/%s/features", base_url, encodedTenantID)
	return DoGetReq(ctx, reqUrl)
}

// sends get request  for tenant rds details to proxy
func getTenantRdsDetails(ctx context.Context, tenantID string) (*models.Response, error) {
	encodedTenantID := url.QueryEscape(tenantID)
	reqUrl := fmt.Sprintf("%s/tenant/%s/rds", base_url, encodedTenantID)
	return DoGetReq(ctx, reqUrl)
}

// sends get request  for tenant open search details to proxy
func getTenantOpeanSeachDetails(ctx context.Context, tenantID string) (*models.Response, error) {
	encodedTenantID := url.QueryEscape(tenantID)
	reqUrl := fmt.Sprintf("%s/tenant/%s/os", base_url, encodedTenantID)
	return DoGetReq(ctx, reqUrl)
}
