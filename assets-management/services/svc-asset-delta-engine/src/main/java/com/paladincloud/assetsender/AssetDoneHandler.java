package com.paladincloud.assetsender;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paladincloud.common.DaggerServerComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AssetDoneHandler implements RequestHandler<SQSEvent, Integer> {

    private static final Logger LOGGER = LogManager.getLogger(AssetDoneHandler.class);

    @Override
    public Integer handleRequest(SQSEvent event, Context context) {
        var componentResolver = DaggerServerComponent.create();
        var parser = Pattern.compile("([^\"]\\S*|\".+?\")\\s*");
        for (var message : event.getRecords()) {
            var body = message.getBody();
            if (body.contains("\"Message\" :")) {
                try {
                    var result = new ObjectMapper().readValue(body, HashMap.class);
                    body = result.get("Message").toString();
                    if (body == null) {
                        LOGGER.error("Unable to find 'Message' in JSON body: {}", body);
                        continue;
                    }
                } catch (JsonProcessingException e) {
                    LOGGER.error("Unable to parse SNS message", e);
                    continue;
                }
            }
            var matches = parser.matcher(body);
            var args = new ArrayList<String>();
            while (matches.find()) {
                args.add(matches.group().replace("\"", ""));
            }
            componentResolver.buildAssetSenderJob().run("AssetShipper", args.toArray(new String[0]));
        }

        return 0;
    }
}
