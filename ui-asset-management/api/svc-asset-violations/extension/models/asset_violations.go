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

type Violation struct {
	PolicyId       string `json:"policyId"`
	PolicyName     string `json:"policyName"`
	Severity       string `json:"severity"`
	Category       string `json:"category"`
	LastScanStatus string `json:"lastScan"`
	IssueId        string `json:"issueId"`
}

type SeverityInfo struct {
	Severity string `json:"severity"`
	Count    int    `json:"count"`
}

type PolicyViolations struct {
	Violations      []Violation    `json:"violations"`
	TotalPolicies   int            `json:"totalPolicies"`
	TotalViolations int            `json:"totalViolations"`
	Compliance      int            `json:"compliance"`
	SeverityInfos   []SeverityInfo `json:"severityInfo"`
	Coverage        string         `json:"coverage"`
}

type AssetViolations struct {
	Data    PolicyViolations `json:"data"`
	Message string           `json:"message"`
}
