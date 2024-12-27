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

// OpenSearchDomainProperties represents the properties of the OpenSearch domain
type OpenSearchDomainProperties struct {
	Endpoint       string `dynamodbav:"endpoint" json:"endpoint"`
	ID             string `dynamodbav:"id" json:"id"`
	KibanaEndpoint string `dynamodbav:"kibana_endpoint" json:"kibana_endpoint"`
}

// OpenSearchProperties represents the output structure for tenant configurations
type OpenSearchProperties struct {
	EsDomain OpenSearchDomainProperties `dynamodbav:"datastore_es_ESDomain" json:"datastore_es_ESDomain"`
}
