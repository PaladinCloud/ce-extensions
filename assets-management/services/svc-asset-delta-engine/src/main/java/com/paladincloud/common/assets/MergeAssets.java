package com.paladincloud.common.assets;

import com.paladincloud.common.errors.JobException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Getter
public class MergeAssets {

    private static final Logger LOGGER = LogManager.getLogger(MergeAssets.class);

    private final Map<String, AssetDTO> updatedAssets = new HashMap<>();
    private final Map<String, AssetDTO> missingAssets = new HashMap<>();
    private final Map<String, AssetDTO> newAssets = new HashMap<>();
    // newPrimaryAssets is only populated when processing secondary sources AND the primary asset
    // is missing
    private final Map<String, AssetDTO> newPrimaryAssets = new HashMap<>();
    // deletedPrimaryAssets is only populated when processing secondary sources AND the primary
    // asset exists AND the last opinion was removed.
    private final List<AssetDTO> deletedPrimaryAssets = new ArrayList<>();
    private final List<AssetDTO> deletedOpinionAssets = new ArrayList<>();

    private MergeAssets() {
    }

    /**
     * Given the existing repository documents, merge in the associated new/latest documents. The
     * existing documents will be updated in place. In addition, this instance will track and
     * provide the new document ids and the deleted document ids.
     *
     * @param assetHelper    - the document builder/updater
     * @param existingAssets - the documents in the repository (OpenSearch)
     * @param latestAssets   - the mapper documents
     * @param primaryAssets  - may be null; for secondary sources, all the existing primary assets.
     * @return - A MergeAssets instance
     */
    static public MergeAssets process(AssetDocumentHelper assetHelper,
        Map<String, AssetDTO> existingAssets, List<Map<String, Object>> latestAssets,
        Map<String, AssetDTO> primaryAssets) {
        var response = new MergeAssets();

        var latestAssetsDataMap = new HashMap<String, Map<String, Object>>();
        latestAssets.forEach(latestDoc -> {
            var idField = latestDoc.getOrDefault(assetHelper.getIdField(), "").toString();
            if (idField.isEmpty()) {
                throw new JobException(
                    STR."Asset missing the id field '\{assetHelper.getIdField()}'");
            }
            var docId = assetHelper.buildDocId(latestDoc);
            latestAssetsDataMap.put(docId, latestDoc);
            var asset = existingAssets.get(docId);
            var isNew = asset == null || (assetHelper.isPrimarySource()
                && asset.getPrimaryProvider() == null);
            if (isNew) {
                response.newAssets.put(docId, assetHelper.createFrom(latestDoc));
                response.updatedAssets.remove(docId);
            } else {
                response.updatedAssets.put(docId, asset);
                assetHelper.updateFrom(latestDoc, asset);
            }
        });

        existingAssets.forEach((key, value) -> {
            if (!isAssetProcessed(key, response)) {
                if (assetHelper.isPrimarySource()) {
                    var assetState = value.getAssetState();
                    if (assetState == null || !assetState.equals(AssetState.SUSPICIOUS)) {
                        response.missingAssets.put(key, value);
                        assetHelper.missing(value);
                    }
                } else {
                    if (assetHelper.missing(value)) {
                        response.deletedOpinionAssets.add(value);
                        var primaryAsset = primaryAssets.get(key);
                        if (primaryAsset != null && primaryAsset.getPrimaryProvider() == null) {
                            response.deletedPrimaryAssets.add(primaryAsset);
                        }
                    } else {
                        response.updatedAssets.put(key, value);
                    }
                }
            }
        });

        // If opinions are being processed, primaryAssets is valid
        if (primaryAssets != null) {
            var activeAssets = new HashMap<>(response.getUpdatedAssets());
            activeAssets.putAll(response.getNewAssets());
            activeAssets.keySet().forEach(key -> {
                if (!primaryAssets.containsKey(key)) {
                    var data = latestAssetsDataMap.get(key);
                    if (data == null) {
                        LOGGER.error("Missing mapper asset data for key '{}'", key);
                    } else {
                        response.newPrimaryAssets.put(key,
                            assetHelper.createPrimaryFromOpinionData(data));
                    }
                }
            });
        }
        return response;
    }

    static private boolean isAssetProcessed(String key, MergeAssets mergeAssets) {
        return mergeAssets.updatedAssets.containsKey(key) || mergeAssets.newAssets.containsKey(key);
    }

    public Map<String, AssetDTO> getExistingAssets() {
        var allAssets = new HashMap<>(getUpdatedAssets());
        allAssets.putAll(getNewAssets());
        allAssets.putAll(getMissingAssets());
        return Collections.unmodifiableMap(allAssets);
    }
}
