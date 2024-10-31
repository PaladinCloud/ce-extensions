package com.paladincloud.assetstate;

import com.paladincloud.common.assets.AssetTypesHelper;
import com.paladincloud.common.errors.JobException;
import com.paladincloud.common.jobs.JobExecutor;
import java.util.List;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AssetStateJob extends JobExecutor {

    private static final Logger LOGGER = LogManager.getLogger(AssetStateJob.class);

    private static final String ASSET_TYPE = "asset_type";
    private static final String DATA_SOURCE = "data_source";
    private static final String EVALUATION_TYPE = "evaluation_type";
    private static final String OMIT_POLICY_EVENT = "omit_policy_event";

    private final AssetTypesHelper assetTypesHelper;

    @Inject
    AssetStateJob(AssetTypesHelper assetTypesHelper) {
        this.assetTypesHelper = assetTypesHelper;
    }

    @Override
    protected void execute() {
        var dataSource = params.get(DATA_SOURCE);
        var assetType = params.get(ASSET_TYPE);
        var evaluationType = params.get(EVALUATION_TYPE);

        LOGGER.info("Starting Asset State job: {}", params);
        // determine type of work
        //  1: Policy state changed - change status for a class (cloud/type) (to either managed or unmanaged)
        //          Ignores suspicious & reconciling, can set to managed or unmanaged
        //  2: Assets shipped - determine and set status for a class
        //          Ignores reconciling, can set to managed, unmanaged or suspicious
        //  3: Reconciliation completed - determine and set status for specific assets
        //          Can set to managed, unmanaged or suspicious

        // Get cloud/type policy status
        var isTypeManaged = assetTypesHelper.isTypeManaged(dataSource, assetType);

        switch (evaluationType.toLowerCase()) {
            case "assets-shipped":
                // Get opinions
                // Get primary assets
                break;
            case "policy-changed":
                break;
            case "reconciliation-completed":
                break;
            default:
                throw new JobException(STR."Invalid evaluation type: '\{evaluationType}'");
        }

        // do the work

        // Send events
        if ("true".equalsIgnoreCase(params.get(OMIT_POLICY_EVENT))) {
            LOGGER.info("Omitting policy event");
        } else {
            // TODO: Fire off policy event
            LOGGER.error("TODO - fire off the policy event");
        }
    }

    @Override
    protected List<String> getRequiredFields() {
        return List.of(ASSET_TYPE, DATA_SOURCE, EVALUATION_TYPE);
    }

    @Override
    protected List<String> getOptionalFields() {
        return List.of(OMIT_POLICY_EVENT);
    }
}
