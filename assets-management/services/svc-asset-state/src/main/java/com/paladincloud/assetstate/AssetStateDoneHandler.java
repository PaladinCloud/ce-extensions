package com.paladincloud.assetstate;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AssetStateDoneHandler implements RequestHandler<SQSEvent, Integer> {

    private static final Logger LOGGER = LogManager.getLogger(AssetStateDoneHandler.class);

    @Override
    public Integer handleRequest(SQSEvent event, Context context) {
        var componentResolver = DaggerServerComponent.create();
        var mapper = new ObjectMapper();
        for (var message : event.getRecords()) {
            try {
                var body = message.getBody();
                if (body.contains("\"Message\"")) {
                    try {
                        var result = new ObjectMapper().readValue(body, HashMap.class);
                        body = result.get("Message").toString();
                        if (body == null) {
                            LOGGER.error("Unable to find 'Message' in JSON body: {}", body);
                            continue;
                        }
                    }  catch (JsonProcessingException e) {
                        LOGGER.error("Unable to parse SNS message", e);
                        continue;
                    }
                }

                var startMessage = mapper.readValue(body, StartMessage.class);
                componentResolver.buildAssetStateJob().run("AssetState", startMessage);
            } catch (Exception e) {
                LOGGER.error("error occurred in AssetState service", e);
            }
        }

        return 0;
    }
}

