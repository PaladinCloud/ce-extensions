package com.paladincloud.assetstate;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Builder
public class AssetStateEvaluator {

    private static final Logger LOGGER = LogManager.getLogger(AssetStateEvaluator.class);
    @Getter
    private final Set<PartialAssetDTO> updated = new HashSet<>();

    @NonNull
    private Map<String, PartialAssetDTO> primaryAssets;
    @NonNull
    private boolean isManaged;

    public void run() {
        // Check each primary as well
        var newState = isManaged ? AssetState.MANAGED : AssetState.UNMANAGED;
        primaryAssets.values().forEach(doc -> {
            if (doc.getPrimaryProvider() == null) {
                setAssetState(doc, newState);
            } else if (StringUtils.isBlank(doc.getPrimaryProvider())) {
                setAssetState(doc, AssetState.SUSPICIOUS);
            } else {
                setAssetState(doc, newState);
            }
        });
    }

    private void setAssetState(PartialAssetDTO asset, AssetState assetState) {
        // If there's no change in the asset state, don't track it as it doesn't need
        // to be updated.
        if (!assetState.equals(asset.getAssetState())) {
            if (assetState.equals(AssetState.SUSPICIOUS)) {
                asset.setAssetState(AssetState.SUSPICIOUS);
            } else {
                if (isManaged) {
                    asset.setAssetState(AssetState.MANAGED);
                } else {
                    asset.setAssetState(AssetState.UNMANAGED);
                }
            }
            updated.add(asset);
        }
    }
}
