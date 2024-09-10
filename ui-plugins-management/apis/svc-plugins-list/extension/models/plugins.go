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

// Plugin represents the structure of each item in the inbound and outbound arrays.
type Plugin struct {
	Source               string       `db:"source" json:"source"`
	Name                 string       `db:"name" json:"name"`
	Type                 string       `db:"type" json:"type"`
	Description          string       `db:"description" json:"description"`
	IconUrl              string       `db:"iconURL" json:"iconUrl"`
	IsLegacy             bool         `db:"isLegacy" json:"isLegacy"`
	DisablePolicyActions bool         `db:"disablePolicyActions" json:"disablePolicyActions"`
	IsInbound            bool         `db:"isInbound" json:"isInbound"`
	IsHidden             bool         `db:"isHidden" json:"isHidden"`
	Flags                FeatureFlags `db:"-" json:"flags"` // This field is not in the database.
}

// Plugins represents the overall structure of the plugins list JSON data.
type Plugins struct {
	Inbound  []Plugin `json:"inbound"`
	Outbound []Plugin `json:"outbound"`
}
