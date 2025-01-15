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

import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.paladincloud.datamapper.model.MappedDataResult;
import com.paladincloud.datamapper.model.PluginDoneReceiveEvent;
import com.paladincloud.datamapper.service.mappers.Mapper;
import com.paladincloud.datamapper.service.mappers.WizMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.paladincloud.datamapper.commons.MapperConstants.*;

public class WizMapperService extends CommonMapperService {
    private static final Logger logger = LoggerFactory.getLogger(WizMapperService.class);
    private ConcurrentHashMap<String, MappedDataResult> policies = new ConcurrentHashMap<>();

    public WizMapperService(PluginDoneReceiveEvent receivedEvent) {
        super(receivedEvent);
    }

    @Override
    public void processRequest() {
        String rawDataFileName = mapperConfigFolder + "assets.json";
        if (!this.s3Client.doesObjectExist(mapperBucket, rawDataFileName)) {
            logger.error("Mapper data file not found: {}", destinationFolder);
            return;
        }
        S3Object mapperFile = this.s3Client.getObject(new GetObjectRequest(mapperBucket, rawDataFileName));
        String mapperFileKey = mapperFile.getKey();
        if (!mapperFileKey.endsWith(MAPPER_FILE_EXTENSION)) {
            return;
        }
        Map<String, Object> mapperConfig = getMapperConfig(mapperFileKey);
        List<MappedDataResult> mappedDataList = getRawFiles(receivedEvent.getPath()).getObjectSummaries()
                .stream().filter(os -> !os.getKey().endsWith("_violations.json")).collect(Collectors.toList())
                .parallelStream().map(rawFile -> processRawFiles(mapperConfig, rawFile)
                ).filter(Objects::nonNull).collect(ArrayList::new, ArrayList::addAll, ArrayList::addAll);
        rawDataFileName = mapperConfigFolder + "violations.json";
        mapperFile = this.s3Client.getObject(new GetObjectRequest(mapperBucket, rawDataFileName));
        mapperFileKey = mapperFile.getKey();
        if (mapperFileKey.endsWith(MAPPER_FILE_EXTENSION)) {
            Map<String, Object> policyMapperConfig = getMapperConfig(mapperFileKey);
            mappedDataList.addAll(getRawFiles(receivedEvent.getPath()).getObjectSummaries()
                    .stream().filter(os -> os.getKey().endsWith("_violations.json")).collect(Collectors.toList())
                    .parallelStream().map(rawFile -> processRawFiles(policyMapperConfig, rawFile)
                    ).filter(Objects::nonNull).collect(ArrayList::new, ArrayList::addAll, ArrayList::addAll));
        }
        mappedDataList.addAll(policies.values());
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
        if (!isDevMode) {
            for (Map.Entry<String, String> entry : dataPathsToTrigger.entrySet()) {
                if (!availableClouds.contains(entry.getKey())) {
                    continue;
                }
                logger.info("Triggering data path: {}", entry.getValue());
                try {
                    SQSService.getInstance().sendLegacyMessage(entry, receivedEvent, true);
                } catch (Exception e) {
                    logger.error("Exception while sending message", e);
                }
            }
        }
        logger.info("Processing done, uploaded {} output files for {} data sources", mappedDataList.size(), dataPathsToTrigger.size());
    }

    private List<MappedDataResult> processRawFiles(Map<String, Object> mapperConfig, S3ObjectSummary rawFile) {
        List<MappedDataResult> result = getMappedDataResults(receivedEvent, mapperConfig, rawFile);
        if (result == null) {
            return null;
        }
        List<MappedDataResult> toReturn = new ArrayList<>();
        for (MappedDataResult mappedData : result) {
            if (mappedData.getExternalDatasource() != null &&
                    !mappedData.getExternalDatasource().isEmpty() &&
                    mappedData.getFileName().endsWith("-policy.data") &&
                    mappedData.getContent() != null && !mappedData.getContent().isEmpty()) {
                if (!policies.containsKey(mappedData.getExternalDatasource())) {
                    policies.put(mappedData.getExternalDatasource(), mappedData);
                } else {
                    if (mappedData.getItemsCount() < 1) {
                        continue;
                    }
                    MappedDataResult existing = policies.get(mappedData.getExternalDatasource());
                    if (existing.getItemsCount() < 1) {
                        existing.setContent(mappedData.getContent());
                    } else {
                        existing.setContent(existing.getContent()
                                .substring(0, existing.getContent().length() - 1) +
                                "," +
                                mappedData.getContent().substring(1));
                    }
                    existing.setItemsCount(existing.getItemsCount() + mappedData.getItemsCount());
                    policies.put(mappedData.getExternalDatasource(), existing);
                }
            } else {
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
            }
        }
        return toReturn;
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
        String rawDataFileName = receivedEvent.getSource() + FILE_NAME_DELIMITER + mapperFileName;


        if( pluginCommonFields != null && !pluginCommonFields.isEmpty()
                && pluginCommonFields.containsKey(mapperFileName)){
            mapperConfig.put(TARGET_TYPE_COMMON_FIELDS,pluginCommonFields.get(mapperFileName));
            pluginCommonFields.remove(rawDataFileName);
        }
        return mapperConfig;
    }

    @Override
    protected List<MappedDataResult> getMappedDataResults(PluginDoneReceiveEvent receivedEvent, String mapperFileName, Map<String, Object>  mapperFields) {

        try {

            // Raw data filename
            String rawDataFileName = receivedEvent.getSource() + FILE_NAME_DELIMITER + mapperFileName;
            String rawDataFilePath = receivedEvent.getPath() + "/" + rawDataFileName;

            // TODO: Extract S3 functionality to a separate service (?)
            // Read raw data
            if (!this.s3Client.doesObjectExist(receivedEvent.getBucketName(), rawDataFilePath)) {
                logger.error("Raw data file not found: {}", rawDataFilePath);
                return null;
            }
            S3Object rawDataFile = this.s3Client.getObject(new GetObjectRequest(receivedEvent.getBucketName(),
                    rawDataFilePath));

            // Init mapper
            Mapper mapper = new WizMapper(receivedEvent, mapperFields, rawDataFile);

            // Map the data
            return mapper.execute();
        } catch (Exception e) {
            // TODO: Add handlers for different types of issues
            logger.error("Failed to map data", e);
            return null;
        }
    }

    protected List<MappedDataResult> getMappedDataResults(PluginDoneReceiveEvent receivedEvent,
                                                          Map<String, Object> mapperConfig,
                                                          S3ObjectSummary rawDataFileSummary) {

        try {
            String rawDataFilePath = rawDataFileSummary.getKey();

            // TODO: Extract S3 functionality to a separate service (?)
            // Read raw data
            if (!this.s3Client.doesObjectExist(rawDataFileSummary.getBucketName(), rawDataFilePath)) {
                logger.error("Raw data file not found: {}", rawDataFilePath);
                return null;
            }
            S3Object rawDataFile = this.s3Client.getObject(new GetObjectRequest(receivedEvent.getBucketName(),
                    rawDataFilePath));

            // Init mapper
            Mapper mapper = new WizMapper(receivedEvent, mapperConfig, rawDataFile);

            // Map the data
            return mapper.execute();
        } catch (Exception e) {
            // TODO: Add handlers for different types of issues
            logger.error("Failed to map data for {}", rawDataFileSummary.getKey(), e);
            return null;
        }
    }
}
