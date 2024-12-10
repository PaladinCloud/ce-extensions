package com.paladincloud.assetstate;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                var startMessage = mapper.readValue(message.getBody(), StartMessage.class);
                componentResolver.buildAssetStateJob().run("AssetState", startMessage);
            } catch (Exception e) {
                LOGGER.error("error occurred in AssetState service", e);
            }
        }

        return 0;
    }
}

