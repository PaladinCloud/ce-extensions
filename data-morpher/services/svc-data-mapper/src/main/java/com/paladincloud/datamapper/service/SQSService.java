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
package com.paladincloud.datamapper.service;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paladincloud.datamapper.commons.MapperConstants;
import com.paladincloud.datamapper.model.DataShipperTriggerEvent;
import com.paladincloud.datamapper.model.PluginDoneReceiveEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SQSService {
    private static SQSService instance = null;

    private static final Logger logger = LoggerFactory.getLogger(SQSService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AmazonSQS sqs;

    private final String env = System.getenv(MapperConstants.ENV);
    private final String INTERNAL_DNS_URL = System.getenv(MapperConstants.INTERNAL_DNS_URL);
    private final String configCreds = System.getenv(MapperConstants.CONFIG_CREDS);

    private SQSService() {
        this.sqs = AmazonSQSClientBuilder.defaultClient();
    }

    public static synchronized SQSService getInstance() {
        if (instance == null)
            instance = new SQSService();

        return instance;
    }

    public void sendMessage(String queueUrl, String messageBody, String groupId) {
        logger.info("SQS message  - {}", messageBody);
        SendMessageRequest sendRequest = new SendMessageRequest()
            .withQueueUrl(queueUrl)
            .withMessageBody(messageBody)
            .withMessageGroupId(groupId);

        SendMessageResult results = this.sqs.sendMessage(sendRequest);
        logger.info("SQS message sent | results - {}", results);
    }

    public void sendLegacyMessage(Map.Entry<String, String> entry, PluginDoneReceiveEvent receiveEvent, boolean isCompositePlugin) throws JsonProcessingException {
        DataShipperTriggerEvent event = generateDataShipperSQSMessage(entry, receiveEvent, isCompositePlugin);
        logger.debug("Sending Mapper Done event - {}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(event));

        sendMessage(System.getenv(MapperConstants.MAPPING_DONE_QUEUE_URL), this.objectMapper.writeValueAsString(event), receiveEvent.getTenantName());
    }

    public DataShipperTriggerEvent generateDataShipperSQSMessage(Map.Entry<String, String> entry, PluginDoneReceiveEvent receiveEvent,
                                                                 boolean isCompositePlugin) {
        DataShipperTriggerEvent.EnvironmentVariable environmentVariable = new DataShipperTriggerEvent.EnvironmentVariable(
                MapperConstants.CONFIG_URL,
                 this.INTERNAL_DNS_URL + "/api/config/batch," + entry.getKey() + "-discovery/prd/latest"
        );

        List<DataShipperTriggerEvent.JobParam> params = Arrays.asList(
                new DataShipperTriggerEvent.JobParam(false, "package_hint", "com.tmobile.cso.pacman"),
                new DataShipperTriggerEvent.JobParam(false, "config_creds", this.configCreds),
                new DataShipperTriggerEvent.JobParam(false, "datasource", entry.getKey()),
                new DataShipperTriggerEvent.JobParam(false, "tenant_id", receiveEvent.getTenantId()),
                new DataShipperTriggerEvent.JobParam(false, "tenant_name", receiveEvent.getTenantName()),
                new DataShipperTriggerEvent.JobParam(false, "s3.data", entry.getValue()),
                new DataShipperTriggerEvent.JobParam(false, "is_composite_plugin",
                        Boolean.toString(isCompositePlugin))
        );

        DataShipperTriggerEvent shipperMessage = new DataShipperTriggerEvent(
                "data-shipper-" + entry.getKey(),
                "data-shipper",
                "jar",
                "Ship " + entry.getKey() + " Data from S3 to Paladin ES",
                Collections.singletonList(environmentVariable),
                params
        );

        return shipperMessage;
    }
}
