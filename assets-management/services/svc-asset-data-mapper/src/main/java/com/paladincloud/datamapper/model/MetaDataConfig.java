/*******************************************************************************
 * Copyright 2023 Paladin Cloud, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package com.paladincloud.datamapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetaDataConfig {
    @JsonProperty("plugin_name")
    private String pluginSource;
    @JsonProperty("asset_type")
    private String assetType;
    @JsonProperty("file_name")
    private String fileName;
    @JsonProperty("raw_file_name")
    private String rawFileName;
    @JsonProperty("relation_mapping")
    private List<RelationMapping> relationMappings;
    @JsonProperty("filtered_outputs")
    private List<FilteredOutput> filteredOutputs;
    @JsonProperty("policy_mapping")
    private Map<String, Object> policyMapping;
    @JsonProperty("is_rules_data")
    private boolean isRulesData;
    @JsonProperty("rule_key")
    private String ruleKey;
    @JsonProperty("post_process_fn")
    private String postProcessFn;
    @JsonProperty("delta_engine_enabled")
    private String deltaEngineEnabled;
}

