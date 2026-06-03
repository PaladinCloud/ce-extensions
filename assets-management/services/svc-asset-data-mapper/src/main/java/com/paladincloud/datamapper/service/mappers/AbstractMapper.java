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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paladincloud.datamapper.commons.MapperConstants;
import com.paladincloud.datamapper.model.*;
import com.paladincloud.datamapper.utils.MapperServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.paladincloud.datamapper.commons.MapperConstants.*;

public abstract class AbstractMapper implements Mapper {
    private static final Logger logger = LoggerFactory.getLogger(AbstractMapper.class);
    protected PluginDoneReceiveEvent receivedEvent;
    protected MetaDataConfig metaDataConfig;
    protected Map<String, Object> mapperConfig;
    protected Map<String, Object> conditionalMapperConfig = new HashMap<>();
    protected S3Object rawDataFile;
    protected final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
    protected final ObjectMapper objectMapper = new ObjectMapper();

    public AbstractMapper(PluginDoneReceiveEvent receivedEvent,
                          Map<String, Object> mapperConfig, S3Object rawDataFile) {
        this.mapperConfig = mapperConfig;
        if (this.mapperConfig.containsKey(MapperConstants.FIELD_NAME_METADATA)) {
            this.metaDataConfig = this.objectMapper.convertValue(this.mapperConfig.get(MapperConstants.FIELD_NAME_METADATA),
                    MetaDataConfig.class);
        }
        this.objectMapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true);
        this.receivedEvent = receivedEvent;
        this.rawDataFile = rawDataFile;
    }

    protected boolean isDeltaEngineEnabled() {
        return "true".equalsIgnoreCase(metaDataConfig.getDeltaEngineEnabled());
    }

    /**
     * Creates mapping between current target type and other types (primary key relations).
     *
     * @return Map<String, Object>
     */
    protected Map<String, Object> generateMultiSourceObject() {
        Map<String, Object> sourceMap = new HashMap<>();
        if (this.metaDataConfig == null) {
            throw new IllegalArgumentException("Metadata is missing in the mapper config");
        }
        if (this.metaDataConfig.getRelationMappings() != null && !this.metaDataConfig.getRelationMappings().isEmpty()) {
            List<RelationMapping> relationMappingList = this.metaDataConfig.getRelationMappings();
            relationMappingList.forEach(fileMetaData -> {
                /* source object level 1 */
                sourceMap.put(fileMetaData.getAssetType(),
                        convertSourceRawDataToMapObject(fileMetaData));
            });
        }

        return sourceMap;
    }

    private Map<String, Object> convertSourceRawDataToMapObject(RelationMapping fileMapper) {
        String s3FilePath = receivedEvent.getPath() + "/" + receivedEvent.getSource() + FILE_NAME_DELIMITER
                + fileMapper.getAssetType()
                + MAPPER_FILE_EXTENSION;
        S3Object s3Object = this.s3Client.getObject(new GetObjectRequest(receivedEvent.getBucketName(), s3FilePath));
        /* source object level 2 */
        Map<String, Object> sourceObjectMap = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(s3Object.getObjectContent()))) {
            String line;
            // read each line and convert to Map object, using source primary key as Map key
            while ((line = reader.readLine()) != null) {
                ObjectMapper objectMapper = new ObjectMapper();
                /* source object level 3 */
                HashMap<String, Object> sourceObject = objectMapper.readValue(line, HashMap.class);
                if (sourceObject.containsKey(fileMapper.getPrimaryKey())) {
                    sourceObjectMap.put((String) sourceObject.get(fileMapper.getPrimaryKey()), sourceObject);
                }
            }
        } catch (Exception e) {
            logger.error("Error in reading {}", fileMapper.getAssetType(), e);
        }

        return sourceObjectMap;
    }

    protected void addFilteredOutputs(List<Map<String, Object>> mappedData, List<MappedDataResult> results) {
        List<FilteredOutput> filteredOutputs = this.metaDataConfig.getFilteredOutputs();
        if (filteredOutputs != null && !filteredOutputs.isEmpty()) {
            filteredOutputs.forEach(filteredOutput ->
            {
                List<Map<String, Object>> filteredData = new ArrayList<>(mappedData);
                List<Map<String, String>> filters = filteredOutput.getFilters();
                String outputFileName = filteredOutput.getFileName();
                try {
                    if( filteredOutput.getFieldsToRemove() != null && !filteredOutput.getFieldsToRemove().isEmpty()){
                        filteredData = MapperServiceUtils.removeFields(filteredData, filteredOutput.getFieldsToRemove().split(","));
                    }
                    MappedDataResult filteredMappedDataResult = getMappedDataResult(
                            outputFileName,
                            filteredOutput.getExternalDatasource(),
                            filteredData,
                            filters);
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

    /**
     * Creates mapped data results based on the filter.
     *
     * @param filteredOutput - filters
     * @return MappedDataResult
     * @throws JsonProcessingException
     */
    protected MappedDataResult getMappedDataResult(FilteredOutput filteredOutput, List<Map<String, Object>> mappedData)
            throws JsonProcessingException {
        List<Map<String, Object>> filteredData = mappedData;
        if (filteredOutput.getFilters() != null && !filteredOutput.getFilters().isEmpty()) {
            filteredData = filteredData.stream().filter(mp -> MapperServiceUtils.checkData(mp,
                    filteredOutput.getFilters())).collect(Collectors.toList());
        }
        String replacedJson = generateOutputJson(filteredData);

        return new MappedDataResult(filteredOutput.getFileName(), filteredOutput.getExternalDatasource(),
                replacedJson, filteredData.size(), isDeltaEngineEnabled());
    }

    /**
     * Creates mapped data results based on the filter.
     *
     * @param fileName           - name of the file
     * @param externalDatasource - external datasource name which is used as part of path to distinguish results
     * @param filters            - filters
     * @return MappedDataResult
     * @throws JsonProcessingException
     */
    protected MappedDataResult getMappedDataResult(String fileName, String externalDatasource,
                                                   List<Map<String, Object>> mappedData,
                                                   List<Map<String, String>> filters) throws JsonProcessingException {
        List<Map<String, Object>> filteredData = mappedData;
        if (filters != null && !filters.isEmpty()) {
            filteredData = filteredData.stream().filter(mp -> MapperServiceUtils.checkData(mp, filters)).collect(Collectors.toList());
        }

        String replacedJson = generateOutputJson(filteredData);

        return new MappedDataResult(fileName, externalDatasource, replacedJson, filteredData.size(), isDeltaEngineEnabled());
    }

    protected MappedDataResult getMappedDataResult(List<Map<String, Object>> mappedData,
                                                   FilteredOutput filteredOutput) throws JsonProcessingException {
        List<Map<String, Object>> filteredData = mappedData;
        List<Map<String, String>> filters = filteredOutput.getFilters();
        if (filters != null && !filters.isEmpty()) {
            filteredData = filteredData.stream().filter(mp ->
                            MapperServiceUtils.checkData(mp, filters) &&
                                    (mp.get("targetType") == null || (mp.get("targetType") != null &&
                                            mp.get("targetType").toString().equalsIgnoreCase(filteredOutput.getTargetType()))))
                    .collect(Collectors.toList());
        }
        String replacedJson = generateOutputJson(filteredData);
        return new MappedDataResult(filteredOutput.getFileName(), filteredOutput.getExternalDatasource(),
                replacedJson, filteredData.size(), isDeltaEngineEnabled());
    }

    /**
     * Generates output json which will be placed in resulting file.
     *
     * @param mappedData - mapped data
     * @return String
     * @throws JsonProcessingException
     */
    private String generateOutputJson(List<Map<String, Object>> mappedData) throws JsonProcessingException {
        String replacedJson;
        try {
            String json = objectMapper.writeValueAsString(mappedData);
            if (mapperConfig.get(REPLACEMENT_KEY) != null) {
                replacedJson = MapperServiceUtils.replaceNodeNames((Map<String, String>) mapperConfig.get(REPLACEMENT_KEY), json);
            } else {
                replacedJson = json;
            }
        } catch (Exception ex) {
            logger.error("Unable to replace fieldNames with replacement json", ex);
            replacedJson = this.objectMapper.writeValueAsString(mappedData);
        }

        return replacedJson;
    }
}
