package com.paladincloud.assetstate;

import com.paladincloud.common.assets.AssetTypesHelper;
import com.paladincloud.common.aws.AssetStorageHelper;
import com.paladincloud.common.errors.JobException;
import com.paladincloud.common.jobs.JobExecutor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final AssetStorageHelper searchHelper;

    @Inject
    AssetStateJob(AssetTypesHelper assetTypesHelper, AssetStorageHelper searchHelper) {
        this.assetTypesHelper = assetTypesHelper;
        this.searchHelper = searchHelper;
    }

    @Override
    protected void execute() {
        var dataSource = params.get(DATA_SOURCE);
        var assetType = params.get(ASSET_TYPE);
        var evaluationType = params.get(EVALUATION_TYPE);

        LOGGER.info("Starting Asset State job: {}", params);
        // determine type of work
        //  1: Policy state changed - change status for a class (cloud/type) (to either managed or unmanaged)
        //          Ignores suspicious & reconciling
        //          Sets to managed or unmanaged
        //  2: Evaluate all - determine and set status for a class (cloud/type)
        //          This is used by both the Delta Engine & Reconciler
        //          Ignores reconciling
        //          Sets to managed, unmanaged or suspicious

        // Get cloud/type policy status
        var isTypeManaged = assetTypesHelper.isTypeManaged(dataSource, assetType);

        switch (evaluationType.toLowerCase()) {
            case "evaluate-all":
                var opinion = searchHelper.getOpinions(dataSource, assetType);
                var primary = searchHelper.getPrimary(dataSource, assetType);
                var evaluator = AssetStateEvaluator.builder()
                    .primaryAssets(toMap(primary))
                    .opinions(toMap(opinion))
                    .isManaged(isTypeManaged)
                    .build();

                evaluator.run();
                searchHelper.setStates(dataSource, assetType, evaluator.getUpdated());

                if (evaluator.getUpdated().isEmpty()) {
                    LOGGER.info("None of the {} {} {} asset states were changed", primary.size(),
                        dataSource, assetType);
                } else {
                    LOGGER.info("{} of {} {} {} asset states were changed to {}",
                        evaluator.getUpdated().size(), primary.size(), dataSource, assetType,
                        isTypeManaged ? AssetState.MANAGED : AssetState.UNMANAGED);
                }
                break;
            case "policy-changed":
                // Toggle asset state ONLY for those assets with the opposite status
                var oldState = isTypeManaged ? AssetState.UNMANAGED : AssetState.MANAGED;
                var newState = isTypeManaged ? AssetState.MANAGED : AssetState.UNMANAGED;
                searchHelper.toggleState(dataSource, assetType, oldState, newState);
                break;
            default:
                throw new JobException(
                    String.format("Invalid evaluation type: '%s'", evaluationType));
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

    Map<String, PartialAssetDTO> toMap(Collection<PartialAssetDTO> assets) {
        return assets.stream()
            .collect(Collectors.toMap(PartialAssetDTO::getDocId, Function.identity()));
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
