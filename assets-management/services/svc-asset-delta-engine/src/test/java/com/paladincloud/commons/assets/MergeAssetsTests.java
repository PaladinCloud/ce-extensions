package com.paladincloud.commons.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.paladincloud.common.AssetDocumentFields;
import com.paladincloud.common.assets.AssetDTO;
import com.paladincloud.common.assets.AssetDocumentHelper;
import com.paladincloud.common.assets.AssetState;
import com.paladincloud.common.assets.MergeAssets;
import com.paladincloud.common.util.JsonHelper;
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
            asset.setDocId(id);
            asset.setLegacyDocId(id);
            asset.setSource("aws");
            asset.setPrimaryProvider("");

            existing.put(asset.getDocId(), asset);
        });
        return existing;
    }

    static private List<Map<String, Object>> createLatest(List<String> ids) {
        List<Map<String, Object>> latest = new ArrayList<>();
        ids.forEach(id -> {
            Map<String, Object> doc = new HashMap<>();
            doc.put("id", id);
            doc.put("source", "aws");
            doc.put("reporting_source", "aws");
            doc.put(AssetDocumentFields.RESOURCE_NAME, STR."name \{id}");
            doc.put(AssetDocumentFields.LAST_SCAN_DATE, "2024-07-21 13:13:00+0000");
            latest.add(doc);
        });
        return latest;
    }

    static private AssetDocumentHelper getHelper(ZonedDateTime startTime, String dataSource,
        String type) {
        return AssetDocumentHelper.builder()
            .loadDate(startTime)
            .idField("id")
            .docIdFields(List.of("id"))
            .reportingSource(dataSource)
            .displayName(type)
            .tags(List.of())
            .type(type)
            .accountIdToNameFn( (_) -> null)
            .assetState(AssetState.MANAGED)
            .resourceNameField("resource_name")
            .build();
    }

    // Verify new/added assets are identified properly
    @Test
    void allNewAreIdentified() {
        var existing = createExisting(List.of());
        var latest = createLatest(List.of("q13"));

        var creator = getHelper(ZonedDateTime.now(), "test3", "ec3");
        var merger = MergeAssets.process(creator, existing, latest, null);

        assertEquals(Set.of(), merger.getMissingAssets().keySet());
        assertEquals(Set.of("test3_ec3_q13"), merger.getNewAssets().keySet());
        assertEquals(Set.of(), merger.getUpdatedAssets().keySet());
    }

    // Verify new/added assets are created and populated with critical fields
    @Test
    void newAreCreated() {
        var existing = createExisting(List.of());
        var latest = createLatest(List.of("q13"));

        var creator = getHelper(ZonedDateTime.now(), "test", "ec2");
        var merger = MergeAssets.process(creator, existing, latest, null);
        assertEquals(Set.of("test_ec2_q13"), merger.getNewAssets().keySet());

        var created = merger.getNewAssets().get("test_ec2_q13");
        assertNotNull(created);
        assertEquals("test_ec2_q13", created.getDocId());
    }

    // Verify no longer present assets are identified properly
    @Test
    void allMissingAreIdentified() {
        var existing = createExisting(List.of("test3_ec3_q13"));
        var latest = createLatest(List.of());

        var creator = getHelper(ZonedDateTime.now(), "test", "ec2");
        var merger = MergeAssets.process(creator, existing, latest, null);

        assertEquals(Set.of("test3_ec3_q13"), merger.getMissingAssets().keySet());
        assertEquals(Set.of(), merger.getNewAssets().keySet());
        assertEquals(Set.of(), merger.getUpdatedAssets().keySet());
        assertTrue(merger.getDeletedOpinionAssets().isEmpty());
        assertTrue(merger.getDeletedPrimaryAssets().isEmpty());
    }

    // Verify existing documents are identified properly
    @Test
    void allUpdatedAreIdentified() {
        var existing = createExisting(List.of("test_ec2_q13"));
        var latest = createLatest(List.of("q13"));

        var creator = getHelper(ZonedDateTime.now(), "test", "ec2");
        var merger = MergeAssets.process(creator, existing, latest, null);

        assertEquals(Set.of(), merger.getMissingAssets().keySet());
        assertEquals(Set.of(), merger.getNewAssets().keySet());
        assertEquals(Set.of("test_ec2_q13"), merger.getUpdatedAssets().keySet());
    }

    @Test
    void updatedAreModified() {
        var docId = "test_ec2_q13";
        var existing = createExisting(List.of(docId));
        existing.get(docId).setResourceName("old name");
        var latest = createLatest(List.of("q13"));

        var creator = getHelper(ZonedDateTime.now(), "test", "ec2");
        MergeAssets.process(creator, existing, latest, null);
        var updated = existing.get(docId);
        assertNotNull(updated);
        assertEquals("name q13", updated.getResourceName());
    }

    @Test
    void missingAreModified() {
        var existing = createExisting(List.of("q13"));
        var latest = createLatest(List.of());

        var creator = getHelper(ZonedDateTime.now(), "test", "ec2");
        var merger = MergeAssets.process(creator, existing, latest, null);

        var removed = merger.getMissingAssets().get("q13");
        assertNotNull(removed);

        assertFalse(removed.getLegacyIsLatest());
    }
}
