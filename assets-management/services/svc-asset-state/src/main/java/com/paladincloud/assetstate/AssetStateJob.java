package com.paladincloud.assetstate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.paladincloud.common.assets.AssetTypesHelper;
import com.paladincloud.common.aws.AssetStorageHelper;
import com.paladincloud.common.aws.SQSHelper;
import com.paladincloud.common.config.ConfigConstants;
import com.paladincloud.common.config.Configuration;
import com.paladincloud.common.jobs.JobExecutor;
import com.paladincloud.common.util.JsonHelper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AssetStateJob extends JobExecutor {

    private static final Logger LOGGER = LogManager.getLogger(AssetStateJob.class);

    private static final String ASSET_TYPES = "asset_types";
    private static final String DATA_SOURCE = "data_source";
    private static final String IS_FROM_POLICY_ENGINE = "is_from_policy_engine";
    private static final String OMIT_POLICY_EVENT = "omit_policy_event";

    private final AssetTypesHelper assetTypesHelper;
    private final AssetStorageHelper searchHelper;
    private final SQSHelper sqsHelper;

    @Inject
    AssetStateJob(AssetTypesHelper assetTypesHelper, AssetStorageHelper searchHelper,
        SQSHelper sqsHelper) {
        this.assetTypesHelper = assetTypesHelper;
        this.searchHelper = searchHelper;
        this.sqsHelper = sqsHelper;
    }

    @Override
    protected void execute() {
        var dataSource = params.get(DATA_SOURCE);
        var assetTypes = params.get(ASSET_TYPES).split(",");
        var isFromPolicyEngine = params.getOrDefault(IS_FROM_POLICY_ENGINE, "false")
            .equalsIgnoreCase("true");
        var omitPolicyEvent = params.getOrDefault(OMIT_POLICY_EVENT, "false")
            .equalsIgnoreCase("true");

        for (var singleAssetType : assetTypes) {
            // Get cloud/type policy status
            var isTypeManaged = assetTypesHelper.isTypeManaged(dataSource, singleAssetType);

            LOGGER.info("Starting Asset State job: {} managed={}", params, isTypeManaged);

            if (isFromPolicyEngine) {
                toggleState(dataSource, singleAssetType, isTypeManaged);
            } else {
                evaluateAssets(dataSource, singleAssetType, isTypeManaged);
            }
        }

        // Send policy engine start event unless the policy engine sent us the event
        if (!isFromPolicyEngine && !omitPolicyEvent) {
            var policyEvent = new PolicyEngineStartEvent(
                String.format("%s-asset-state-%s", dataSource, UUID.randomUUID()),
                dataSource, null,
                assetTypes,
                tenantId, tenantName);
            try {
                LOGGER.info("Sending policy event: {}", JsonHelper.toJson(policyEvent));
            } catch (JsonProcessingException e) {
                // Intentionally ignore
            }

            sqsHelper.sendMessage(Configuration.get(ConfigConstants.SHIPPER_DONE_URL),
                policyEvent,
                UUID.randomUUID().toString());
        }
    }

    // Go through all but the Reconciling assets, updating them to either managed/unmanaged
    // or suspicious
    void evaluateAssets(String dataSource, String assetType, boolean isTypeManaged) {
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
    }

    // Toggle asset state ONLY for those assets with the now old state
    void toggleState(String dataSource, String assetType, boolean isTypeManaged) {
        var oldState = isTypeManaged ? AssetState.UNMANAGED : AssetState.MANAGED;
        var newState = isTypeManaged ? AssetState.MANAGED : AssetState.UNMANAGED;
        searchHelper.toggleState(dataSource, assetType, oldState, newState);
    }

    Map<String, PartialAssetDTO> toMap(Collection<PartialAssetDTO> assets) {
        return assets.stream()
            .collect(Collectors.toMap(PartialAssetDTO::getDocId, Function.identity()));
    }

    @Override
    protected List<String> getRequiredFields() {
        return List.of(ASSET_TYPES, DATA_SOURCE);
    }

    @Override
    protected List<String> getOptionalFields() {
        return List.of(IS_FROM_POLICY_ENGINE, OMIT_POLICY_EVENT);
    }
}
