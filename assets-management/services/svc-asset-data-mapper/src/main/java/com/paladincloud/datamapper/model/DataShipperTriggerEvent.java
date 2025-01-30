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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class DataShipperTriggerEvent {

    private String jobName;
    private String jobUuid;
    private String jobType;
    private String jobDesc;
    private List<EnvironmentVariable> environmentVariables;
    private List<JobParam> params;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvironmentVariable {
        private String name;
        private String value;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobParam {
        private boolean encrypt;
        private String key;
        private String value;
    }
}