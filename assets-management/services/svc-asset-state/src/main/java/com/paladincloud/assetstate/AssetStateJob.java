package com.paladincloud.assetstate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.paladincloud.common.assets.AssetTypesHelper;
import com.paladincloud.common.aws.AssetStorageHelper;
import com.paladincloud.common.aws.SQSHelper;
import com.paladincloud.common.config.ConfigConstants;
import com.paladincloud.common.config.Configuration;
import com.paladincloud.common.errors.JobException;
import com.paladincloud.common.jobs.JobExecutor;
import com.paladincloud.common.util.JsonHelper;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AssetStateJob extends JobExecutor {

    private static final Logger LOGGER = LogManager.getLogger(AssetStateJob.class);

    private static final String OMIT_POLICY_EVENT = "OMIT_POLICY_EVENT";

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
    protected void execute(StartMessage message) {
        var dataSource = message.source();
        var assetTypes = message.assetTypes();
        var isFromPolicyEngine =
            message.isFromPolicyEngine() != null ? message.isFromPolicyEngine() : false;
        var omitPolicyEvent = "true".equalsIgnoreCase(System.getenv(OMIT_POLICY_EVENT));

        for (var singleAssetType : assetTypes) {
            // Get cloud/type policy status
            var isTypeManaged = assetTypesHelper.isTypeManaged(dataSource, singleAssetType);

            LOGGER.info("Starting Asset State job: dataSource={} assetType={} managed={}",
                message.source(), singleAssetType, isTypeManaged);

            if (isFromPolicyEngine) {
                toggleState(dataSource, singleAssetType, isTypeManaged);
            } else {
                evaluateAssets(dataSource, singleAssetType, isTypeManaged);
            }
        }

        // Send policy engine start event unless the policy engine sent us the event
        if (!isFromPolicyEngine) {
            var policyEvent = new PolicyEngineStartEvent(
                String.format("%s-asset-state-%s", dataSource, UUID.randomUUID()),
                dataSource, null,
                assetTypes,
                tenantId, message.tenantName());
            String asJson = null;
            try {
                asJson = JsonHelper.toJson(policyEvent);
            } catch (JsonProcessingException e) {
                throw new JobException("Failed converting event to JSON", e);
            }
            if (omitPolicyEvent) {
                LOGGER.info("OMITTING policy event: {}", asJson);
            } else {
                LOGGER.info("Sending event: {}", asJson);

                sqsHelper.sendMessage(Configuration.get(ConfigConstants.SHIPPER_DONE_URL),
                    policyEvent,
                    UUID.randomUUID().toString());
            }
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

}
