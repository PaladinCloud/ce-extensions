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

type ViolationsOpenSearchResult struct {
	Hits         Hits         `json:"hits"`
	Aggregations Aggregations `json:"aggregations"`
}

type Hits struct {
	Total    Total       `json:"total"`
	MaxScore float64     `json:"max_score"`
	Hits     []InnerHits `json:"hits"`
}

type Total struct {
	Value    int    `json:"value"`
	Relation string `json:"relation"`
}

type InnerHits struct {
	Index   string  `json:"_index"`
	ID      string  `json:"_id"`
	Score   float64 `json:"_score"`
	Routing string  `json:"_routing"`
	Source  Source  `json:"_source"`
}

type Source struct {
	IssueStatus string `json:"issueStatus"`
	PolicyID    string `json:"policyId"`
}

type Aggregations struct {
	Severity Severity `json:"Severity"`
}

type Severity struct {
	DocCountErrorUpperBound int      `json:"doc_count_error_upper_bound"`
	SumOtherDocCount        int      `json:"sum_other_doc_count"`
	Buckets                 []Bucket `json:"buckets"`
}

type Bucket struct {
	Key      string `json:"key"`
	DocCount int    `json:"doc_count"`
}
