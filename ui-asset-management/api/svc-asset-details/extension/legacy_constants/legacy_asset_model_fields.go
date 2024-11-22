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

package legacy_constants

var LegacyCommonFields = map[string]struct{}{
	AccountId:      {},
	AccountName:    {},
	Source:         {},
	SourceName:     {},
	TargetType:     {},
	TargetTypeName: {},
	Region:         {},

	"_docid":  {},
	"docId":   {},
	"docType": {},

	"_entity": {},
	"latest":  {},

	"firstdiscoveredon": {},
	"discoverydate":     {},
	"_discoverydate":    {},
	"_loaddate":         {},

	"_resourceid":        {},
	"name":               {},
	"assetIdDisplayName": {},

	"key":                   {},
	"id":                    {},
	"inBoundSecurityRules":  {},
	"outBoundSecurityRules": {},
	"assetRiskScore":        {},
	"arsLoadDate":           {},
}

const (
	AccountId      = "accountid"
	AccountName    = "accountname"
	Source         = "_cloudType"
	SourceName     = "sourceDisplayName"
	TargetType     = "_entitytype"
	TargetTypeName = "targettypedisplayname"
	Region         = "region"
	AssetState     = "_assetState"
	ResourceId     = "_resourceid"
)

var CommonFields = []string{
	AccountId,
	AccountName,
	Source,
	SourceName,
	TargetType,
	TargetTypeName,
	Region,
	AssetState,
	ResourceId,
}

func IsCommonField(field string) bool {
	_, skip := LegacyCommonFields[field]
	return skip
}
