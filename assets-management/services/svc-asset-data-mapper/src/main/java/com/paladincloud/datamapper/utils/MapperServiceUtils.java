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
package com.paladincloud.datamapper.utils;

import com.jayway.jsonpath.*;
import com.paladincloud.datamapper.commons.FunctionCaller;
import com.paladincloud.datamapper.commons.MapperConstants;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.paladincloud.datamapper.commons.MapperConstants.*;

public class MapperServiceUtils {
    private static final Logger logger = LoggerFactory.getLogger(MapperServiceUtils.class);

    private MapperServiceUtils() {
        throw new IllegalStateException("MapperServiceUtils is a Utility class");
    }

    public static Map<String, Object> createResourceData(DocumentContext resourceJson, Map<String, Object> mapperConfig,
                                                         Map<String, Object> multiSourceObject) {
        Map<String, Object> resourceMap = new HashMap<>();
        if (mapperConfig.containsKey(TARGET_TYPE_COMMON_FIELDS)) {
            resourceMap.putAll(mappingFields(resourceJson,
                    (Map<String, String>) mapperConfig.get(TARGET_TYPE_COMMON_FIELDS), multiSourceObject));
        }
        if (mapperConfig.containsKey(FIELD_NAME_TOP_LEVEL_FIELDS)) {
            resourceMap.putAll(mappingFields(resourceJson,
                    (Map<String, String>) mapperConfig.get(FIELD_NAME_TOP_LEVEL_FIELDS), multiSourceObject));
        }
        if (!resourceMap.isEmpty()) {
            if (mapperConfig.containsKey(FIELD_NAME_CONDITIONAL_TOP_LEVEL_FIELDS)) {
                for(Map<String, Object> conditionalFieldsConfig : (List<Map<String, Object>>) mapperConfig.get(FIELD_NAME_CONDITIONAL_TOP_LEVEL_FIELDS)) {
                    if (conditionalFieldsConfig.containsKey(FIELD_NAME_FILTER_PATTERN) && conditionalFieldsConfig.containsKey(FIELD_NAME_FILTER_FIELD)) {
                        String filterPattern = (String) conditionalFieldsConfig.get(FIELD_NAME_FILTER_PATTERN);
                        String filterField = (String) conditionalFieldsConfig.get(FIELD_NAME_FILTER_FIELD);
                        if (resourceMap.containsKey(filterField) && resourceMap.get(filterField) != null) {
                            if (Pattern.matches(filterPattern, resourceMap.get(filterField).toString())) {
                                resourceMap.putAll(mappingFields(resourceJson, (Map<String, String>) conditionalFieldsConfig.get(FIELD_NAME_FILTER_FIELDS), multiSourceObject));
                            }
                        }
                    }
                }
            }

            if (mapperConfig.containsKey(FIELD_NAME_PAYLOAD)) {
                resourceMap.put(FIELD_NAME_PAYLOAD, mappingFields(resourceJson,
                        (Map<String, String>) mapperConfig.get(FIELD_NAME_PAYLOAD), multiSourceObject));
            }

            if (mapperConfig.containsKey(FIELD_NAME_ADDITIONAL_FIELDS)) {
                resourceMap.put(FIELD_NAME_ADDITIONAL_FIELDS, mappingFields(resourceJson,
                        (Map<String, String>) mapperConfig.get(FIELD_NAME_ADDITIONAL_FIELDS),
                        multiSourceObject));
            }
        }

        return resourceMap;
    }

    public static Map<String, Object> mappingFields(DocumentContext resourceJson, Map<String, String> fieldsConfig,
                                                     Map<String, Object> multiSourceObject) {
        Map<String, Object> resultMap = new HashMap<>();
        try {
            fieldsConfig.keySet().forEach(
                    key -> {
                        String fieldValue = fieldsConfig.get(key);
                        try {
                            if (fieldValue != null && fieldValue.startsWith(JSON_ROOT_PATH)) {
                                resultMap.put(key, resourceJson.read(fieldValue));
                            } else if (fieldValue != null && fieldValue.startsWith("@")) {
                                resultMap.put(key,
                                        FunctionCaller.callFunction(fieldValue, resourceJson, multiSourceObject));
                            } else if (fieldValue != null && fieldValue.startsWith(FIELD_STARTS_WITH_HASH)) {
                                resultMap.put(key, FunctionCaller.getValueFromMultiSourceObject(fieldValue,
                                        resourceJson, multiSourceObject));
                            } else {
                                resultMap.put(key, fieldValue);
                            }
                        } catch (PathNotFoundException e) {
                            logger.warn("Json path is not found for key {}", fieldValue);
                        }
                    });
        } catch (Exception e) {
            logger.error("Failed to map fields", e);
            return Collections.emptyMap();
        }

        return resultMap;
    }

    /**
     * Check if data object passes all filters.
     *
     * @param data   - data
     * @param filter - filter
     * @return boolean
     */
    public static boolean checkData(Map<String, Object> data, List<Map<String, String>> filter) {
        for (Map<String, String> filterMap : filter) {
            String filterField = filterMap.get(FIELD_NAME_FILTER_FIELD);
            String filterPattern = filterMap.get(FIELD_NAME_FILTER_PATTERN);
            if (data.containsKey(filterField)) {
                if (data.get(filterField) == null ||
                        !Pattern.compile(filterPattern).matcher(data.get(filterField).toString()).find()) {
                    return false;
                }
            }
        }

        return true;
    }

    public static String replaceNodeNames(Map<String, String> replacementMap, String json) {
        for (Map.Entry<String, String> entry : replacementMap.entrySet()) {
            String oldName = entry.getValue();
            String newName = entry.getKey();
            json = json.replaceAll("\"\\b" + oldName + "\\b\":", "\"" + newName + "\":");
        }

        return json;
    }

    public static List<Map<String, Object>> removeFields(List<Map<String, Object>> mappedData, String[] fieldsToRemove) {
        Set<String> fieldsToRemoveSet = new HashSet<>(Arrays.asList(fieldsToRemove));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> map : mappedData) {
            Map<String, Object> filteredMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!fieldsToRemoveSet.contains(entry.getKey())) {
                    filteredMap.put(entry.getKey(), entry.getValue());
                }
            }
            result.add(filteredMap);
        }

        return result;
    }
}
