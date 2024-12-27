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

// we can proxy to the following:
// secret manager GET /tenant/{tenantid}/secrets/{key} -> should return a key/value object
// config table GET /tenant/{tenantid}/config/{projection} -> return string/interface{} as we don't know the structure
// output table GET /tenant/{tenantid}/infra/{projection} -> return string/interface{} as we don't know the structure

// Known endpoints

// feature_flags are used to enable/disable a global feature. This flag can be consumed by UI, APIs, Services, etc.
// tenant feature flags GET /tenant/{tenantid}/features -> should return a key/value object from "feature_flags" projection

// ui_feature_flags are used to enable/disable fine grain ui sections
// feature flags -> services GET /tenant/{tenantid}/ui/features -> should return a key/value object from "ui_feature_flags" projection

type Response struct {
	key   string
	value string
}
