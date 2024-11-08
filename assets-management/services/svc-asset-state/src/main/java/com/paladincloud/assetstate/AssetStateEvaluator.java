package com.paladincloud.assetstate;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
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
    private Map<String, PartialAssetDTO> opinions;
    @NonNull
    private boolean isManaged;

    public void run() {
        // Walk through the opinions - any missing primaryProvider data are suspicious
        opinions.keySet().forEach(docId -> {
            var primary = primaryAssets.get(docId);
            if (primary == null) {
                LOGGER.error("An opinion is missing the matching primary asset: docId={}", docId);
            } else {
                if (primary.getPrimaryProvider() == null) {
                    setAssetState(primary, AssetState.SUSPICIOUS);
                }
            }
        });

        // Check each primary as well
        var newState = isManaged ? AssetState.MANAGED : AssetState.UNMANAGED;
        primaryAssets.values().forEach(doc -> {
            switch (doc.getAssetState()) {
                case null:
                case SUSPICIOUS:
                    if (doc.getPrimaryProvider() == null) {
                        setAssetState(doc, AssetState.SUSPICIOUS);
                    } else {
                        setAssetState(doc, newState);
                    }
                    break;
                case MANAGED, UNMANAGED:
                    setAssetState(doc, newState);
                    break;
                case RECONCILING:
                    break;
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
