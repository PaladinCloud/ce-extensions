/*******************************************************************************
 * Copyright 2024 Paladin Cloud, Inc. or its affiliates. All Rights Reserved.
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
package com.paladincloud.datamapper.service.mappers;

import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.StringUtils;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.paladincloud.datamapper.commons.PostProcess;
import com.paladincloud.datamapper.model.MappedDataResult;
import com.paladincloud.datamapper.model.PluginDoneReceiveEvent;
import com.paladincloud.datamapper.model.RelationMapping;
import com.paladincloud.datamapper.utils.MapperServiceUtils;
import com.paladincloud.datamapper.utils.rules.RulesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.paladincloud.datamapper.commons.MapperConstants.JSON_ROOT_PATH;

public class CommonMapper extends AbstractMapper {
    private static final Logger logger = LoggerFactory.getLogger(CommonMapper.class);

    public CommonMapper(PluginDoneReceiveEvent receivedEvent,
                        Map<String, Object> mapperConfig, S3Object rawDataFile) {
        super(receivedEvent, mapperConfig, rawDataFile);
    }

    @Override
    public List<MappedDataResult> execute() throws Exception {
        List<MappedDataResult> results = new ArrayList<>();
        if (this.metaDataConfig.isRulesData()) {
            RulesService.getInstance().loadRules(rawDataFile, this.metaDataConfig.getRuleKey());
        } else {
            Map<String, Object> multiSourceObject = generateMultiSourceObject();
            // Read the text input stream one line at a time and process each line.
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(rawDataFile.getObjectContent()))) {
                List<Map<String, Object>> mappedData = getMappedData(reader, multiSourceObject);
                if (mappedData.isEmpty()) {
                    logger.error("No data found in the file: {}", rawDataFile.getKey());
                    return results;
                }
                /* post-process function applied on the whole dataset, useful for grouping/aggregations  */
                if (!StringUtils.isNullOrEmpty(this.metaDataConfig.getPostProcessFn())) {
                    Class<?> clazz = PostProcess.class;
                    Method method = clazz.getDeclaredMethod(this.metaDataConfig.getPostProcessFn(), List.class);
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    mappedData = (List<Map<String, Object>>) method.invoke(instance, mappedData);
                }
                // If the file name is provided, then add the mapped data to the results to ship
                if (this.metaDataConfig.getFileName() != null) {
                    results.add(getMappedDataResult(this.metaDataConfig.getFileName(), null, mappedData, null));
                }

                addFilteredOutputs(mappedData, results);
            }
        }

        return results;
    }

    private List<Map<String, Object>> getMappedData(BufferedReader reader, Map<String, Object> multiSourceObject) throws IOException {
        List<Map<String, Object>> mappedData = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            DocumentContext resourceJson = JsonPath.parse(line,
                    Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS));
            // If related is mandatory, and any of them is missing, then skip the resource
            if (multiSourceObject != null && isAnyRelatedObjectMissing(multiSourceObject, resourceJson)) {
                continue;
            }

            mappedData.add(MapperServiceUtils.createResourceData(resourceJson, this.mapperConfig, multiSourceObject));
        }

        return mappedData;
    }

    private boolean isAnyRelatedObjectMissing(Map<String, Object> multiSourceObject, DocumentContext resourceJson) {
        List<RelationMapping> relationMappings = this.metaDataConfig.getRelationMappings();
        if (relationMappings != null && !relationMappings.isEmpty()) {
            DocumentContext multiObjectJson = JsonPath.parse(multiSourceObject,
                    Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS));
            for (RelationMapping relationMapping : relationMappings) {
                if (relationMapping.isMandatory()) {
                    Object obj = multiObjectJson.read(JSON_ROOT_PATH.concat(relationMapping.getAssetType()).concat(".")
                            + resourceJson.read(relationMapping.getForeignKey()));
                    if (obj == null) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
