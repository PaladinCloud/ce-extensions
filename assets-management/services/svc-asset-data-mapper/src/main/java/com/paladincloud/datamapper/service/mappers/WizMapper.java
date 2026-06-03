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
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.paladincloud.datamapper.model.FilteredOutput;
import com.paladincloud.datamapper.model.MappedDataResult;
import com.paladincloud.datamapper.model.MetaDataConfig;
import com.paladincloud.datamapper.model.PluginDoneReceiveEvent;
import com.paladincloud.datamapper.utils.MapperServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.paladincloud.datamapper.commons.MapperConstants.*;

public class WizMapper extends AbstractMapper {
    private static final Logger logger = LoggerFactory.getLogger(WizMapper.class);
    private HashMap<String, Map<String, Object>> policies = new HashMap<>();

    public WizMapper(PluginDoneReceiveEvent receivedEvent, Map<String, Object> mapperConfig, S3Object rawDataFile) {
        super(receivedEvent, mapperConfig, rawDataFile);
    }

    public List<MappedDataResult> execute() throws IOException {
        // Read the text input stream one line at a time and process each line.
        List<MappedDataResult> results = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(rawDataFile.getObjectContent()))) {
            InputStream inputStream = getClass().getResourceAsStream("/mappings-legacy/wiz/cloud_mappings.json");
            MetaDataConfig[] metaDataConfigs = objectMapper.readValue(inputStream, MetaDataConfig[].class);
            String[] filenamePath = rawDataFile.getKey().split("/");
            String filename = filenamePath[filenamePath.length - 1];
            boolean isViolation = filename.contains("_violations");
            String rawDataFileName = isViolation ? filename.replace("_violations", "") : filename;
            this.metaDataConfig = Arrays.stream(metaDataConfigs)
                    .filter(conf -> conf.getPluginSource().equalsIgnoreCase("wiz") &&
                            rawDataFileName.equalsIgnoreCase(conf.getRawFileName()))
                    .findFirst().orElse(metaDataConfig);
            if (this.metaDataConfig == null) {
                logger.info("Mapper config not found for raw file {}", filename);
                //return results;
            }
            List<Map<String, Object>> mappedData = getMappedData(reader, isViolation);
            if (mappedData.isEmpty()) {
                logger.error("No data found in the file: {}", rawDataFile.getKey());
                return results;
            }
            if (isViolation) {
                metaDataConfig.getFilteredOutputs().removeIf(fo -> !fo.getFileName().contains("-issues-"));
            }
            if (this.metaDataConfig.getFileName() != null) {
                results.add(getMappedDataResult(this.metaDataConfig.getFileName(), null, mappedData, null));
            }
            // Group by targetType field
            /*if (isViolation) {
                addFilteredOutputs(mappedData, results);
            } else {
                Map<Object, List<Map<String, Object>>> groupedData = mappedData.stream()
                        .collect(Collectors.groupingBy(data -> data.get("targetType")));

                groupedData.forEach((targetType, group) -> {
                    addFilteredOutputs(group, results, String.valueOf(targetType));
                });
            }*/
            addFilteredOutputsByTargetType(mappedData, results);
            addFilteredPolicyOutputs(results);
        }

        return results;
    }

    protected void addFilteredOutputsByTargetType(List<Map<String, Object>> mappedData, List<MappedDataResult> results) {
        List<FilteredOutput> filteredOutputs = this.metaDataConfig.getFilteredOutputs();
        if (filteredOutputs != null && !filteredOutputs.isEmpty()) {
            filteredOutputs.forEach(filteredOutput ->
            {
                try {
                    MappedDataResult filteredMappedDataResult = getMappedDataResult(mappedData, filteredOutput);
                    if (filteredMappedDataResult == null) {
                        logger.error("Unable to apply filter. Error: Incorrect filter configuration");
                    } else {
                        results.add(filteredMappedDataResult);
                    }
                } catch (Exception e) {
                    logger.error("Unable to apply filter.", e);
                }
            });
        }
    }

    private void addFilteredPolicyOutputs(List<MappedDataResult> results) {
        if (policies != null && !policies.isEmpty()) {
            List<FilteredOutput> filteredOutputs = this.metaDataConfig.getFilteredOutputs();
            if (filteredOutputs != null && !filteredOutputs.isEmpty()) {
                for (FilteredOutput filteredOutput : filteredOutputs) {
                    List<Map<String, String>> filters = filteredOutput.getFilters();
                    String outputFileName = filteredOutput.getExternalDatasource() + "-policy.data";
                    try {
                        List<Map<String, Object>> policyList = new ArrayList<>(policies.values());
                        MappedDataResult filteredMappedDataResult = getMappedDataResult(
                                outputFileName,
                                filteredOutput.getExternalDatasource(),
                                policyList,
                                filters);
                        if (filteredMappedDataResult == null) {
                            logger.error("Unable to apply filter. Error: Incorrect filter configuration");
                        } else {
                            results.add(filteredMappedDataResult);
                        }
                    } catch (Exception e) {
                        logger.error("Unable to apply filter.", e);
                    }
                }
            }
        }
    }

    private List<Map<String, Object>> getMappedData(BufferedReader reader, boolean isViolation) throws IOException {
        List<Map<String, Object>> mappedData = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            DocumentContext resourceJson = JsonPath.parse(line,
                    Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS));
            createAndAddMetaDataConfigIfNotExists(resourceJson, isViolation);
            if (isViolation) {
                metaDataConfig.setPolicyMapping((Map<String, Object>) mapperConfig.get("policy_mapping"));
            }
            if (this.conditionalMapperConfig != null && !this.conditionalMapperConfig.isEmpty()) {
                mappedData.add(MapperServiceUtils.createResourceData(resourceJson, this.conditionalMapperConfig, null));
            } else {
                mappedData.add(MapperServiceUtils.createResourceData(resourceJson, this.mapperConfig, null));
            }
            if (metaDataConfig.getPolicyMapping() != null && !metaDataConfig.getPolicyMapping().isEmpty()) {
                Map<String, Object> policyMapperConfig = metaDataConfig.getPolicyMapping();
                if (policyMapperConfig != null) {
                    try {
                        Map<String, Object> policy = MapperServiceUtils.createResourceData(resourceJson, metaDataConfig.getPolicyMapping(), null);
                        if (policy != null && !policy.isEmpty() && policy.get("policyId") != null && !policies.containsKey(policy.get("policyId"))) {
                            policies.put((String) policy.get("policyId"), policy);
                        }
                    } catch (Exception e) {
                        logger.error("Error while processing policy mapping", e);
                    }
                }
            }
        }

        return mappedData;
    }

    public boolean checkData(List<Map<String, String>> filters, DocumentContext resourceJson) {
        if (filters == null) {
            return true;
        }
        for (Map<String, String> entry : filters) {
            String filterField = entry.get(FIELD_NAME_FILTER_FIELD);
            String filterPattern = entry.get(FIELD_NAME_FILTER_PATTERN);
            if (resourceJson.read(filterField) == null ||
                    !Pattern.compile(filterPattern).matcher(resourceJson.read(filterField).toString()).find()) {
                return false;

            }
        }
        return true;
    }

    private void createAndAddMetaDataConfigIfNotExists(DocumentContext resourceJson, boolean isViolation) {
        this.conditionalMapperConfig.clear();
        String cloudType, nativeType;
        String type = resourceJson.read("$.type").toString();
        if (isViolation) {
            cloudType = resourceJson.read("$.cloud_platform").toString().toLowerCase();
            nativeType = resourceJson.read("$.native_type").toString().toLowerCase();
        } else {
            if (resourceJson.read("$.graph_entity.properties.nativeType") != null) {
                nativeType = resourceJson.read("$.graph_entity.properties.nativeType").toString().toLowerCase();
            } else {
                nativeType = type.toLowerCase();
            }
            if (resourceJson.read("$.graph_entity.properties.cloudPlatform") != null) {
                cloudType = resourceJson.read("$.graph_entity.properties.cloudPlatform").toString().toLowerCase();
            } else {
                cloudType = "wiz";
            }
        }
        if (nativeType.matches(".*[^a-zA-Z0-9 ].*")) {
            String[] parts = nativeType.split("[^a-zA-Z0-9 ]");
            nativeType = parts[parts.length - 1];
        }
        String finalNativeType = nativeType;
        if (metaDataConfig != null) {
            if (!metaDataConfig.getFilteredOutputs().isEmpty() && isViolation) {
                metaDataConfig.getFilteredOutputs().stream().filter(fo -> fo
                        .getExternalDatasource().equalsIgnoreCase(cloudType)).forEach(fo -> {
                    fo.setAssetType("violations");
                    String filename = fo.getFileName();
                    if (!filename.contains("-issues")) {
                        filename = cloudType + "-issues-" + fo.getTargetType() + ".data";
                    }
                    fo.setFileName(filename);
                });
            }
            Optional<FilteredOutput> availableMetaDataConfig = metaDataConfig.getFilteredOutputs().stream()
                    .filter(fo -> fo.getExternalDatasource().equalsIgnoreCase(cloudType)
                            && !fo.isAutoCreated()
                            && (checkData(fo.getFileFilter(), resourceJson))
                    ).findAny();
            if (availableMetaDataConfig.isPresent()) {
                resourceJson.put("$", "targetType", availableMetaDataConfig.get().getTargetType());
                resourceJson.put("$", "targetTypeDisplayName", availableMetaDataConfig.get()
                        .getTargetTypeDisplayName());
                /*adding top level fields from filter config*/
                if (!isViolation && availableMetaDataConfig.get().getConditionalTopLevelFields() != null
                        && !availableMetaDataConfig.get().getConditionalTopLevelFields().isEmpty()) {
                    this.conditionalMapperConfig = new HashMap<>(this.mapperConfig);
                    Map<String, String> topLevelFields =
                            new HashMap<>((Map<String, String>) this.mapperConfig.get(FIELD_NAME_TOP_LEVEL_FIELDS));
                    topLevelFields.putAll(availableMetaDataConfig.get().getConditionalTopLevelFields());
                    this.conditionalMapperConfig.put(FIELD_NAME_TOP_LEVEL_FIELDS, topLevelFields);
                }
                return;
            }
            Optional<FilteredOutput> autoCreatedMetaDataConfig = metaDataConfig.getFilteredOutputs().stream()
                    .filter(fo -> fo.getExternalDatasource().equalsIgnoreCase(cloudType) && fo.isAutoCreated() &&
                            fo.getTargetType().equalsIgnoreCase(finalNativeType)).findAny();
            if (autoCreatedMetaDataConfig.isPresent() &&
                    autoCreatedMetaDataConfig.get().getTargetType().equalsIgnoreCase(nativeType)) {
                if (isViolation) {
                    autoCreatedMetaDataConfig.get().setAssetType("violations");
                    String filename = autoCreatedMetaDataConfig.get().getFileName();
                    if (!filename.contains("-issues")) {
                        filename = autoCreatedMetaDataConfig.get().getFileName().replace(cloudType, cloudType + "-issues");
                    }
                    autoCreatedMetaDataConfig.get().setFileName(filename);
                }
                resourceJson.put("$", "targetType", autoCreatedMetaDataConfig.get().getTargetType());
                resourceJson.put("$", "targetTypeDisplayName", autoCreatedMetaDataConfig.get()
                        .getTargetTypeDisplayName());
                return;
            }
        } else {
            metaDataConfig = new MetaDataConfig();
            metaDataConfig.setPluginSource("wiz");
            metaDataConfig.setAssetType(type);
            metaDataConfig.setFilteredOutputs(new ArrayList<>());
        }
        FilteredOutput filteredOutput = new FilteredOutput();
        List<Map<String, String>> filters = new ArrayList<>();
        Map<String, String> filter = new HashMap<>();
        filteredOutput.setExternalDatasource(cloudType);
        filteredOutput.setFileName(cloudType + (isViolation ? "-issues-" : "-") + nativeType + ".data");
        if (isViolation) {
            filteredOutput.setAssetType("violations");
        }
        filteredOutput.setTargetType(nativeType);
        String formattedType = replaceUnderscoreWithSpace(type);
        filteredOutput.setTargetTypeDisplayName(formattedType);
        resourceJson.put("$", "targetType", nativeType);
        resourceJson.put("$", "targetTypeDisplayName", formattedType);
        filteredOutput.setAutoCreated(true);
        filter.put("field", "_cloudType");
        filter.put("pattern", "^" + cloudType);
        filters.add(filter);
        filteredOutput.setFilters(filters);
        metaDataConfig.getFilteredOutputs().add(filteredOutput);
        resourceJson.put("$", "targetType", nativeType);
    }

    public static String replaceUnderscoreWithSpace(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : input.toCharArray()) {
            if (c == '_') {
                result.append(' ');
                capitalizeNext = true;
            } else {
                if (capitalizeNext && Character.isLetter(c)) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }
        }
        return result.toString();
    }
}
