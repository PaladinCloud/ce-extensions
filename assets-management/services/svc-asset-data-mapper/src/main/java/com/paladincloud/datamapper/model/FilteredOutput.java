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
public class FilteredOutput {
    @JsonProperty("asset_type")
    private String assetType;
    @JsonProperty("target_type")
    private String targetType;
    @JsonProperty("target_type_display_name")
    private String targetTypeDisplayName;
    @JsonProperty("auto_created")
    private boolean autoCreated;
    @JsonProperty("file_name")
    private String fileName;
    @JsonProperty("filters")
    private List<Map<String, String>> filters;
    @JsonProperty("external_datasource")
    private String externalDatasource;
    @JsonProperty("conditional_top_level_fields")
    private Map<String, String> conditionalTopLevelFields;
    @JsonProperty("file_filter")
    private List<Map<String, String>> fileFilter;
    @JsonProperty("fields_to_remove")
    private String fieldsToRemove;
}
