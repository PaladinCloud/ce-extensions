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
package com.paladincloud.datamapper.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.paladincloud.datamapper.commons.MapperConstants;
import com.paladincloud.datamapper.model.AssetMappingDoneEvent;
import com.paladincloud.datamapper.model.MappedDataResult;
import com.paladincloud.datamapper.model.PluginDoneReceiveEvent;
import com.paladincloud.datamapper.service.mappers.CommonMapper;
import com.paladincloud.datamapper.service.mappers.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.paladincloud.datamapper.commons.MapperConstants.*;

public class CommonMapperService implements MapperService {
    protected static final boolean isDevMode = System.getenv("DEV_MODE") != null;
    private static final Logger logger = LoggerFactory.getLogger(CommonMapperService.class);
    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
    protected final String mapperBucket = System.getenv(MapperConstants.MAPPER_BUCKET_NAME);
    protected final String destinationBucket = System.getenv(MapperConstants.DESTINATION_BUCKET);
    protected final String destinationFolder = System.getenv(MapperConstants.DESTINATION_FOLDER);
    protected final String mappingDoneOutputArn = System.getenv(MapperConstants.OUTPUT_TOPIC_ARN);
    private final String env = System.getenv(MapperConstants.ENV);
    private final String configCreds = System.getenv(MapperConstants.CONFIG_CREDS);
    protected final List<String> availableClouds = Arrays.asList("aws", "gcp", "azure");
    protected PluginDoneReceiveEvent receivedEvent;
    protected String pluginDatasource;
    protected String mapperConfigFolder;
    protected String destinationPath;
    protected Map<String, Map<String, Object>> pluginCommonFields;
    protected ConcurrentHashMap<String, String> dataPathsToTrigger = new ConcurrentHashMap<>();

    public CommonMapperService(PluginDoneReceiveEvent receivedEvent) {
        this.objectMapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true);
        this.receivedEvent = receivedEvent;
        this.pluginDatasource = receivedEvent.getSource();
        this.mapperConfigFolder = System.getenv(MapperConstants.MAPPER_FOLDER_NAME) + "/" + this.pluginDatasource + "/";
        try {
            this.pluginCommonFields = commonTargetTypeFields(getMapperConfig(mapperConfigFolder+ BASE_ASSET_MAPPER_FILE_NAME));
        } catch(Exception e){
            logger.warn(" basic asset mapper file don't exists");
        }
        this.destinationPath = this.destinationFolder + "/" +
                receivedEvent.getTenantId() + "/" +
                receivedEvent.getSource() + "/" +
                receivedEvent.getScanTime();
    }

    public void processRequest() {
        ObjectListing mapperFiles = getMapperFiles();
        if (mapperFiles.getObjectSummaries().isEmpty()) {
            logger.error("No mapper files found for datasource: {}", this.pluginDatasource);
            return;
        }
        // check if mapper files has a rules file and pre-load the Rules Service
        mapperFiles.getObjectSummaries().stream()
                .filter(mapperFile -> mapperFile.getKey().contains(MAPPER_RULES_KB_FILENAME_NOTATION))
                .forEach(mapperFile -> {
                    String mapperFileName = mapperFile.getKey().substring((mapperFile.getKey().lastIndexOf("/") + 1))
                            .replace("/", FILE_NAME_DELIMITER);
                    Map<String, Object> mapperFields =  targetMapperFields( mapperFile);
                    getMappedDataResults(receivedEvent, mapperFileName, mapperFields);
                });

        mapperFiles.getObjectSummaries().parallelStream().forEach(mapperFile -> {
            String mapperFileName = mapperFile.getKey().substring((mapperFile.getKey().lastIndexOf("/") + 1))
                    .replace("/", FILE_NAME_DELIMITER);
                    if (!BASE_ASSET_MAPPER_FILE_NAME.equals(mapperFileName)) {
                        Map<String, Object> mapperFields =  targetMapperFields( mapperFile);
                        List<MappedDataResult> mappedDataList = getMappedDataResults(receivedEvent,mapperFileName, mapperFields);
                        uploadDataFileToS3(mappedDataList);
                    }
                }

        );
        /* Process remaining target types from the  basic mapper list */
        if(pluginCommonFields != null && !pluginCommonFields.isEmpty()) {
            Set<String> mapperFileNames =  pluginCommonFields.keySet();
            mapperFileNames.forEach( mapperFile -> {
                HashMap<String, Object> mapperFields = new HashMap<>();
                mapperFields.put(TARGET_TYPE_COMMON_FIELDS, pluginCommonFields.get(mapperFile));
                Map<String, Object> metaData = new HashMap<>();
                Map<String, Object> commonFields = pluginCommonFields.get(mapperFile);
                String dataFileName = commonFields.get("_cloudType")+"-"+commonFields.get("targetType")+".data";
                metaData.put("file_name",dataFileName);
                mapperFields.put(FIELD_NAME_METADATA, metaData);
                List<MappedDataResult> mappedDataList =  getMappedDataResults(receivedEvent,mapperFile,mapperFields);
                uploadDataFileToS3(mappedDataList);
            });
        }
        /* Group all vulnerability into one vulnerability.data file */
        s3VulnerabilityDataMerger();

        String envDeltaEngine = System.getenv(ENABLED_DELTA_ENGINE_PLUGINS);
        if (envDeltaEngine == null) {
            envDeltaEngine = "";
        }
        List<String> deltaEnginePlugins = Stream.of(envDeltaEngine.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .collect(Collectors.toList());
        for (Map.Entry<String, String> entry : dataPathsToTrigger.entrySet()) {
            logger.info("Triggering data path: {}", entry.getValue());
            try {
                boolean useDeltaEngine = deltaEnginePlugins.contains(entry.getKey().toLowerCase());
                if (useDeltaEngine) {
                    logger.info("Sending to delta engine");
                    AssetMappingDoneEvent doneEvent = new AssetMappingDoneEvent(receivedEvent.getTenantId(),
                            receivedEvent.getTenantName(),
                        entry.getKey(), entry.getValue(),receivedEvent.getReportingSource(),
                            receivedEvent.getReportingService(), receivedEvent.getReportingServiceDisplayName(),
                            receivedEvent.getSourceDisplayName());
                    if (!isDevMode) {
                        sendMapperDoneSNS(doneEvent.toCommandLine(), receivedEvent.getTenantId());
                    } else {
                        logger.info("Skipping SQS due to dev mode ({})", doneEvent.toCommandLine());
                    }
                } else {
                    if (!isDevMode) {
                        SQSService.getInstance().sendLegacyMessage(entry, receivedEvent, false);
                    } else {
                        logger.info("Skipping SQS due to dev mode ({})", receivedEvent);
                    }
                }
            } catch (Exception e) {
                logger.error("Exception while sending message", e);
            }
        }

        logger.info("Processed number of data sources {}", dataPathsToTrigger.size());
    }

    protected void sendMapperDoneSNS(String message, String groupId) {

        // Create an SNS client
        PublishRequest request = new PublishRequest(mappingDoneOutputArn, message);
        request = request.withMessageGroupId(groupId).withMessageDeduplicationId(groupId);
        AmazonSNS client = AmazonSNSClientBuilder.standard().build();

        // Publish the message to the specified topic
        PublishResult result = client.publish(request);

        // Print the message ID returned by the publish operation
        logger.info("Notification message sent with id {}", result.getMessageId());
    }

    private boolean uploadDataFileToS3( List<MappedDataResult> mappedDataList){
        if (mappedDataList != null) {
            mappedDataList.forEach(mappedData -> {
                String dataFolder = this.destinationPath;
                String datasource = this.pluginDatasource;
                if (mappedData.getExternalDatasource() != null && !mappedData.getExternalDatasource().isEmpty()) {
                    dataFolder += "/" + mappedData.getExternalDatasource();
                    datasource = mappedData.getExternalDatasource();
                }

                if (!dataPathsToTrigger.containsKey(datasource)) {
                    dataPathsToTrigger.put(datasource, dataFolder);
                }
                String filePath = dataFolder + "/" + mappedData.getFileName();
                logger.info("Uploading: {}, size: {}, assets count: {}", filePath, mappedData.getContent().length(), mappedData.getItemsCount());
                this.s3Client.putObject(this.destinationBucket, filePath, mappedData.getContent());
            });

            logger.info("Processing done, uploaded {} output files", mappedDataList.size());
        }
        return true;
    }

    protected Map<String, Object> targetMapperFields(S3ObjectSummary mapperFile){
        if (!mapperFile.getKey().endsWith(MAPPER_FILE_EXTENSION)) {
            return null;
        }
        String mapperFileKey = mapperFile.getKey();
        Map<String, Object> mapperConfig = getMapperConfig(mapperFileKey);
        if (Objects.isNull(mapperConfig)) {
            logger.error("Failed to get mapper config from {}", mapperFileKey);
            return null;
        }
        String mapperFileName = mapperFileKey.substring((mapperFileKey.lastIndexOf("/") + 1))
                .replace("/", FILE_NAME_DELIMITER);
        if( pluginCommonFields != null && !pluginCommonFields.isEmpty()
                && pluginCommonFields.containsKey(mapperFileName)){
            mapperConfig.put(TARGET_TYPE_COMMON_FIELDS,pluginCommonFields.get(mapperFileName));
            pluginCommonFields.remove(mapperFileName);
        }
        return mapperConfig;
    }


    protected List<MappedDataResult> getMappedDataResults(PluginDoneReceiveEvent receivedEvent,
                                                          String mapperFileName, Map<String, Object>  mapperFileds) {

        try {
            String rawDataFileName = receivedEvent.getSource() + FILE_NAME_DELIMITER + mapperFileName;
            String rawDataFilePath = receivedEvent.getPath() + "/" + rawDataFileName;
            if (!this.s3Client.doesObjectExist(receivedEvent.getBucketName(), rawDataFilePath)) {
                logger.error("Raw data file not found: {}", rawDataFilePath);
                return null;
            }
            S3Object rawDataFile = this.s3Client.getObject(new GetObjectRequest(receivedEvent.getBucketName(),
                    rawDataFilePath));
            Mapper mapper = new CommonMapper(receivedEvent, mapperFileds, rawDataFile);
            return mapper.execute();
        } catch (Exception e) {
            logger.error("Failed to map data", e);
            return null;
        }
    }

    protected Map<String, Object> getMapperConfig(String fileName) {
        String mapperBucketName = System.getenv(MapperConstants.MAPPER_BUCKET_NAME);
        try {
            S3Object object = this.s3Client.getObject(new GetObjectRequest(mapperBucketName, fileName));
            Map<String, Object> config = this.objectMapper.readValue(object.getObjectContent(), Map.class);
            if (Objects.isNull(config)) {
                return Collections.emptyMap();
            }
            return config;
        } catch (IOException e) {
            logger.error("Error in reading {} in s3", fileName, e);
        } catch (Exception e) {
            logger.error("File {} not found in s3 ", fileName, e);
        }
        return Collections.emptyMap();
    }

    protected ObjectListing getMapperFiles() {
        ObjectListing mapperFiles = this.s3Client.listObjects(mapperBucket, mapperConfigFolder);
        logger.info("Mapping files in folder {}: {}", mapperConfigFolder, mapperFiles.getObjectSummaries().size());
        return mapperFiles;
    }

    protected ObjectListing getRawFiles(String path) {
        ObjectListing mapperFiles = this.s3Client.listObjects(destinationBucket, path);
        logger.info("Raw files in folder {}: {}", destinationFolder, mapperFiles.getObjectSummaries().size());
        return mapperFiles;
    }

    protected Map<String, Map<String, Object>> commonTargetTypeFields(Map<String, Object> jsonMap) {
        Map<String, Map<String, Object>> resultMap = new HashMap<>();
        Map<String, Object> commonFields = (Map<String, Object>) jsonMap.get(COMMON_FIELDS);
        Iterable<Map<String, Object>> targetSpecificFields = (Iterable<Map<String, Object>>) jsonMap.get(TARGET_TYPE_COMMON_FIELDS);
        for (Map<String, Object> targetTypeField : targetSpecificFields) {
            String rawDataFile = (String) targetTypeField.get(RAW_DATA_FILE_NAME);
            targetTypeField.remove(RAW_DATA_FILE_NAME);
            Map<String, Object> combinedFields = new HashMap<>(commonFields);
            combinedFields.putAll(targetTypeField);
            resultMap.put(rawDataFile, combinedFields);
        }
        return resultMap;
    }

    /* group all vulnerabilities  */
    private void s3VulnerabilityDataMerger() {
        String dataFolder = this.destinationPath;
        String datasource = this.pluginDatasource;
        String outputFileName = datasource + "-vulnerabilities.data";
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            // Fetch object summaries
            List<S3ObjectSummary> matchingFiles = s3Client.listObjects(this.destinationBucket, dataFolder)
                    .getObjectSummaries()
                    .stream()
                    .filter(summary -> summary.getKey().endsWith("-vulnerabilities.data"))
                    .collect(Collectors.toList());

            // Proceed only if there are multiple matching files
            if (matchingFiles.size() <= 1) {
                logger.info("No need to merge as matching files count is {}", matchingFiles.size());
                return;
            }

            // Merge JSON data from all matching files
            ArrayNode mergedData = objectMapper.createArrayNode();
            for (S3ObjectSummary summary : matchingFiles) {
                try (S3Object s3Object = s3Client.getObject(this.destinationBucket, summary.getKey());
                     InputStream inputStream = s3Object.getObjectContent()) {
                    ArrayNode fileData = (ArrayNode) objectMapper.readTree(inputStream);
                    mergedData.addAll(fileData);
                } catch (Exception e) {
                    logger.error("Error processing file {}: {}", summary.getKey(), e.getMessage());
                }
            }

            // Write merged data to S3
            String mergedDataJson = objectMapper.writeValueAsString(mergedData);
            s3Client.putObject(this.destinationBucket, dataFolder + "/" +outputFileName, mergedDataJson);
            logger.info("Merged data saved to {}", outputFileName);
        } catch (Exception e) {
            logger.error("Error during S3 vulnerability data merging: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }






}
