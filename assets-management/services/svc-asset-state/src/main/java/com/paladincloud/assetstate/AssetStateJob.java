package com.paladincloud.assetstate;

import com.paladincloud.common.jobs.JobExecutor;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javax.inject.Inject;

public class AssetStateJob extends JobExecutor {
    private static final Logger LOGGER = LogManager.getLogger(AssetStateJob.class);

    private static final String ASSET_TYPE = "asset_type";
    private static final String DATA_SOURCE = "data_source";
    private static final String OMIT_POLICY_EVENT = "omit_policy_event";

    @Inject
    AssetStateJob() {
    }

    @Override
    protected void execute() {
        var dataSource = params.get(DATA_SOURCE);
        var assetType = params.get(ASSET_TYPE);

        // needed config
        //  opensearch
        //      url
        //  mysql
        //      host
        //      username
        //      password

        // determine type of work
        //  1: change status to given value (managed or unmanaged)
        //  2: determine and set status, optionally for specific assets

        // do the work

        // Send events
        if ("true".equalsIgnoreCase(params.get(OMIT_POLICY_EVENT))) {
            LOGGER.info("Omitting policy event");
        } else {
            // TODO: Fire off policy event
        }
    }

    @Override
    protected List<String> getRequiredFields() {
        return List.of(ASSET_TYPE, DATA_SOURCE);
    }
}
