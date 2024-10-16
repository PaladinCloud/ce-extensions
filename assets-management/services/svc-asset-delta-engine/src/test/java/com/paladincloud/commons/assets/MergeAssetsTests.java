package com.paladincloud.commons.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.paladincloud.common.AssetDocumentFields;
import com.paladincloud.common.assets.AssetDTO;
import com.paladincloud.common.assets.AssetDocumentHelper;
import com.paladincloud.common.assets.AssetState;
import com.paladincloud.common.assets.MergeAssets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class MergeAssetsTests {

    static private Map<String, AssetDTO> createExisting(List<String> ids) {
        Map<String, AssetDTO> existing = new HashMap<>();
        ids.forEach(id -> {
            var asset = new AssetDTO();
            asset.setLegacyDocId(id);

            existing.put(asset.getLegacyDocId(), asset);
        });
        return existing;
    }

    static private List<Map<String, Object>> createLatest(List<String> ids) {
        List<Map<String, Object>> latest = new ArrayList<>();
        ids.forEach(id -> {
            Map<String, Object> doc = new HashMap<>();
            doc.put("id", id);
            doc.put(AssetDocumentFields.RESOURCE_NAME, STR."name \{id}");
            doc.put(AssetDocumentFields.LAST_DISCOVERY_DATE, "2024-07-21 13:13:00+0000");
//            doc.put(AssetDocumentFields.REPORTING_SOURCE, "aws");
            latest.add(doc);
        });
        return latest;
    }

    static private AssetDocumentHelper getHelper(ZonedDateTime startTime, String dataSource,
        String type, String resourceNameField) {
        return AssetDocumentHelper.builder()
            .loadDate(startTime)
            .idField("id")
            .docIdFields(List.of("id"))
            .dataSource(dataSource)
            .isCloud(true)
            .displayName(type)
            .tags(List.of())
            .type(type)
            .accountIdToNameFn( (_) -> null)
            .assetState(AssetState.MANAGED)
            .resourceNameField(resourceNameField)
            .build();
    }

    // Verify new/added assets are identified properly
    @Test
    void allNewAreIdentified() {
        var existing = createExisting(List.of());
        var latest = createLatest(List.of("q13"));

        var creator = getHelper(ZonedDateTime.now(), "test3", "ec3", "resource_name");
        var merger = MergeAssets.process(creator, existing, latest);

        assertEquals(Set.of(), merger.getRemovedAssets().keySet());
        assertEquals(Set.of("q13"), merger.getNewAssets().keySet());
        assertEquals(Set.of(), merger.getUpdatedAssets().keySet());
    }

    // Verify new/added assets are created and populated with critical fields
    @Test
    void newAreCreated() {
        var existing = createExisting(List.of());
        var latest = createLatest(List.of("q13"));

        var creator = getHelper(ZonedDateTime.now(), "test", "ec2", "resource_name");
        var merger = MergeAssets.process(creator, existing, latest);
        assertEquals(Set.of("q13"), merger.getNewAssets().keySet());

        var created = merger.getNewAssets().get("q13");
        assertNotNull(created);
        assertEquals("q13", created.getLegacyDocId());
    }

    // Verify no longer present assets are identified properly
    @Test
    void allRemovedAreIdentified() {
        var existing = createExisting(List.of("q13"));
        var latest = createLatest(List.of());

        var creator = getHelper(ZonedDateTime.now(), "test", "ec2", "resource_name");
        var merger = MergeAssets.process(creator, existing, latest);

        assertEquals(Set.of("q13"), merger.getRemovedAssets().keySet());
        assertEquals(Set.of(), merger.getNewAssets().keySet());
        assertEquals(Set.of(), merger.getUpdatedAssets().keySet());
    }

    // Verify existing documents are identified properly
    @Test
    void allUpdatedAreIdentified() {
        var existing = createExisting(List.of("q13"));
        var latest = createLatest(List.of("q13"));

        var creator = getHelper(ZonedDateTime.now(), "test", "ec2", "resource_name");
        var merger = MergeAssets.process(creator, existing, latest);

        assertEquals(Set.of(), merger.getRemovedAssets().keySet());
        assertEquals(Set.of(), merger.getNewAssets().keySet());
        assertEquals(Set.of("q13"), merger.getUpdatedAssets().keySet());
    }

    @Test
    void updatedAreModified() {
        var existing = createExisting(List.of("q13"));
        existing.get("q13").setResourceName("old name");
        var latest = createLatest(List.of("q13"));

        var creator = getHelper(ZonedDateTime.now(), "test", "ec2", "resource_name");
        MergeAssets.process(creator, existing, latest);
        var updated = existing.get("q13");
        assertNotNull(updated);
        assertEquals("name q13", updated.getResourceName());
    }

    @Test
    void removedAreModified() {
        var existing = createExisting(List.of("q13"));
        var latest = createLatest(List.of());

        var creator = getHelper(ZonedDateTime.now(), "test", "ec2", "resource_name");
        var merger = MergeAssets.process(creator, existing, latest);

        var removed = merger.getRemovedAssets().get("q13");
        assertNotNull(removed);

        assertFalse(removed.isLegacyIsLatest());
    }
}
