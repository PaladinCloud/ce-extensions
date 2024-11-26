package com.paladincloud.assetstate;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AssetStateDoneHandler implements RequestHandler<SQSEvent, Integer> {

    private static final Logger LOGGER = LogManager.getLogger(AssetStateDoneHandler.class);

    @Override
    public Integer handleRequest(SQSEvent event, Context context) {
        var componentResolver = DaggerServerComponent.create();
        for (var message : event.getRecords()) {
            try {
                var args = message.getBody().split(" ");
                componentResolver.buildAssetStateJob().run("AssetState", args);
            } catch (Exception e) {
                LOGGER.error("error occurred in AssetState service", e);
            }
        }

        return 0;
    }
}

