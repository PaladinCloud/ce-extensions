package com.paladincloud.assetsender;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.paladincloud.common.DaggerServerComponent;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class AssetDoneHandler implements RequestHandler<SQSEvent, Integer> {

    @Override
    public Integer handleRequest(SQSEvent event, Context context) {
        var componentResolver = DaggerServerComponent.create();
        var parser = Pattern.compile("([^\"]\\S*|\".+?\")\\s*");
        for (var message : event.getRecords()) {
            var matches = parser.matcher(message.getBody());
            var args = new ArrayList<String>();
            while (matches.find()) {
                args.add(matches.group().replace("\"", ""));
            }
            componentResolver.buildAssetSenderJob().run("AssetShipper", args.toArray(new String[0]));
        }

        return 0;
    }
}
