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

package models

import (
	"time"
)

const (
	success = "success"
	osUrl   = "osUrl"
)

func ConvertRdsSecretToResponse(secret *RdsSecret) *Response {
	return &Response{
		Data: map[string]interface{}{
			"DB_USERNAME": secret.DbUsername,
			"DB_PASSWORD": secret.DbPassword,
			"DB_NAME":     secret.DbName,
			"RDS_HOST":    secret.DbHost,
			"RDS_PORT":    secret.DbPort,
		},
		Message: success,
	}
}

func ConvertOsPropertiesToResponse(osProperties *OpenSearchProperties) *Response {
	return &Response{
		Data: map[string]interface{}{
			osUrl: osProperties.EsDomain.Endpoint,
		},
		Message: success,
	}
}

func ConvertTenantFeatureFlagsToResponse(flags TenantFeatureFlags) *Response {
	response := Response{
		Data:    make(map[string]interface{}),
		Message: success,
	}
	now := time.Now()

	for featureName, flag := range flags.FeatureFlags {
		// A feature is considered active if it's enabled and either has no expiration date or is not expired
		isActive := flag.Status && (flag.ExpirationDate == nil || now.Before(*flag.ExpirationDate))
		response.Data[featureName] = isActive
	}

	return &response
}

func ConvertSecretToResponse(secret map[string]interface{}) *Response {
	return &Response{
		Data:    secret,
		Message: success,
	}
}

func ConvertAssetDetailToResponse(assetDetails map[string]interface{}) *Response {
	return &Response{
		Data:    assetDetails,
		Message: success,
	}
}
