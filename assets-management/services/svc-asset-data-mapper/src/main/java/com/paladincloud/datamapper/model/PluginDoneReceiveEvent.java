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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PluginDoneReceiveEvent {
    private String bucketName;
    private String path;
    private String source;
    private String[] accounts;
    @JsonProperty("tenant_id")
    private String tenantId;
    @JsonProperty("tenant_name")
    private String tenantName;
    private String scanTime;
    private String reportingSource;
    @JsonProperty("reporting_source_service")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String reportingService;
    @JsonProperty("reporting_source_service_display_name")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String reportingServiceDisplayName;
    @JsonProperty("source_display_name")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String sourceDisplayName;

    @JsonSetter("source")
    public void setSource(String source) {
        this.source = source;
        this.reportingSource = source; // Sets reportingSource to the same value
    }
}
