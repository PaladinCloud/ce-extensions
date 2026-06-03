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
package com.paladincloud.datamapper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paladincloud.datamapper.model.PluginDoneReceiveEvent;
import com.paladincloud.datamapper.service.MapperService;
import com.paladincloud.datamapper.utils.MapperServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class StartMapper implements RequestHandler<SQSEvent, String> {
    private static final Logger logger = LoggerFactory.getLogger(StartMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StartMapper() {
        // Removing CompilerPath debug logging hack
        LoggerContext logContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger compiledPathLog = logContext.getLogger("com.jayway.jsonpath.internal.path.CompiledPath");
        ch.qos.logback.classic.Logger jsonContextLog = logContext.getLogger("com.jayway.jsonpath.internal.JsonContext");
        compiledPathLog.setLevel(Level.INFO);
        jsonContextLog.setLevel(Level.INFO);
        objectMapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true);
    }

    @Override
    public String handleRequest(SQSEvent event, Context context) {
        final Date startTime = logStartProcess();
        try {
            for (SQSEvent.SQSMessage msg : event.getRecords()) {
                logger.info("Received event: {}", msg.getBody());
                PluginDoneReceiveEvent receivedEvent = this.objectMapper.readValue(msg.getBody(),
                        PluginDoneReceiveEvent.class);

                logger.info("Datasource: {}", receivedEvent.getSource());

                MapperService mapperService = MapperServiceFactory.getMapperService(receivedEvent);
                // Process the message
                mapperService.processRequest();
            }
        } catch (Exception e) {
            logger.error("Exception", e);
            logEndProcess(startTime);

            return "DataMapper run status = failed";
        }

        logEndProcess(startTime);
        return "DataMapper run status = success";
    }

    private Date logStartProcess() {
        Date startTime = new Date();
        logger.info("DataMapper Started: {}", startTime);

        return startTime;
    }

    private void logEndProcess(Date startTime) {
        Date endTime = new Date();
        logger.info("DataMapper Finished: {}", endTime);
        logger.debug("Total ran for: {}", endTime.getTime() - startTime.getTime());
    }
}
