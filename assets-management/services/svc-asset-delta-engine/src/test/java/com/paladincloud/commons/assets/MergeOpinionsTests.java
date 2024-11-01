package com.paladincloud.commons.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.paladincloud.common.AssetDocumentFields;
import com.paladincloud.common.assets.AssetDTO;
import com.paladincloud.common.assets.AssetDTO.OpinionCollection;
import com.paladincloud.common.assets.AssetDTO.OpinionItem;
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

public class MergeOpinionsTests {

    static private final String defaultRawData = """
        { "flavor": "banana", "size": "extraLarge" }
        """;
    static private final String secondRawData = """
        { "flavor": "licorice", "size": "superExtraLarge" }
        """;

    static private final String defaultReportingService = "assets";
    static private final String secondReportingService = "vulnerabilities";
    static private final String defaultReportingSource = "secondary";
    static private final String secondReportingSource = "another";

    static public Map<String, AssetDTO> createExistingOpinion(List<String> ids, String dataSource,
        String reportingSource, String rawData) {
        Map<String, AssetDTO> existing = new HashMap<>();
        ids.forEach(id -> {
            var asset = new AssetDTO();
            asset.setDocId(id);
            asset.setSource(reportingSource);
            asset.setOpinions(new OpinionCollection());
            var opinionItem = new OpinionItem();
            opinionItem.setData(rawData != null ? rawData : defaultRawData);
            asset.getOpinions().setOpinion(dataSource, defaultReportingService, opinionItem);

            existing.put(asset.getDocId(), asset);
        });
        return existing;
    }

    static public void addSecondOpinion(AssetDTO asset, String source, String service,
        String rawData) {
        var opinionItem = new OpinionItem();
        opinionItem.setData(rawData);
        asset.getOpinions().setOpinion(source, service, opinionItem);
    }

    static public Map<String, AssetDTO> createExistingPrimary(List<String> ids,
        String reportingSource, String rawData) {
        Map<String, AssetDTO> existing = new HashMap<>();
        ids.forEach(id -> {
            var asset = new AssetDTO();
            asset.setDocId(id);
            asset.setSource(reportingSource);
            asset.setPrimaryProvider(rawData);

            existing.put(asset.getDocId(), asset);
        });
        return existing;
    }

    static public List<Map<String, Object>> createLatest(List<String> ids, String dataSource,
        String reportingSource, String rawData) {
        List<Map<String, Object>> latest = new ArrayList<>();
        ids.forEach(id -> {
            Map<String, Object> doc = new HashMap<>();
            doc.put("id", id);
            doc.put("source", dataSource);
            doc.put("reporting_source", reportingSource);
            doc.put(AssetDocumentFields.RESOURCE_NAME, STR."name \{id}");
            doc.put(AssetDocumentFields.LAST_SCAN_DATE, "2024-07-21 13:13:00+0000");
            doc.put("rawData", rawData != null ? rawData : defaultRawData);
            latest.add(doc);
        });
        return latest;
    }

    static private AssetDocumentHelper getHelper(String reportingSource, String reportingService) {
        return AssetDocumentHelper.builder()
            .loadDate(ZonedDateTime.now())
            .idField("id")
            .docIdFields(List.of("id"))
            .dataSource("gcp")
            .displayName("vminstance")
            .tags(List.of())
            .type("vminstance")
            .accountIdToNameFn((_) -> null)
            .assetState(AssetState.MANAGED)
            .resourceNameField("resource_name")
            .reportingSource(reportingSource)
            .reportingSourceService(reportingService)
            .build();
    }

    // Given neither a primary nor an opinion, an opinion is added
    @Test
    void newOpinionAdded() {
        var existingOpinions = createExistingOpinion(List.of(), defaultReportingSource, "gcp",
            null);
        var latest = createLatest(List.of("q13"), "gcp", defaultReportingSource, null);
        var existingPrimary = createExistingPrimary(List.of(), "gcp", null);

        var creator = getHelper(defaultReportingSource, defaultReportingService
        );
        var merger = MergeAssets.process(creator, existingOpinions, latest, existingPrimary);

        assertEquals(Set.of("gcp_vminstance_q13"), merger.getNewAssets().keySet());
        assertEquals(Set.of(), merger.getMissingAssets().keySet());
        assertEquals(Set.of(), merger.getUpdatedAssets().keySet());
        assertEquals(List.of(), merger.getDeletedOpinionAssets());
    }

    // Given an existing stub and opinion, update the opinion
    @Test
    void existingOpinionUpdated() {
        var existingOpinions = createExistingOpinion(List.of("gcp_vminstance_q13"),
            defaultReportingSource, "gcp", secondRawData);
        var latest = createLatest(List.of("q13"), "gcp", defaultReportingSource, null);
        var existingPrimary = createExistingPrimary(List.of(), "gcp", null);

        var creator = getHelper(defaultReportingSource, defaultReportingSource
        );
        var merger = MergeAssets.process(creator, existingOpinions, latest, existingPrimary);

        var asset = merger.getUpdatedAssets().get("gcp_vminstance_q13");
        assertNotNull(asset);
        var opinionItem = asset.getOpinions()
            .getSourceAndServiceOpinion(defaultReportingSource, defaultReportingService);
        assertNotNull(opinionItem);
        assertEquals(secondRawData, opinionItem.getData());
    }

    // Given an existing stub and opinion, an additional opinion from the same source is added
    @Test
    void additionalOpinionAdded() {
        var existingOpinions = createExistingOpinion(List.of("gcp_vminstance_q13"),
            defaultReportingSource, "gcp", defaultRawData);
        var latest = createLatest(List.of("q13"), defaultReportingSource, "gcp", secondRawData);
        var existingPrimary = createExistingPrimary(List.of(), "gcp", null);

        var creator = getHelper(defaultReportingSource, secondReportingService);
        var merger = MergeAssets.process(creator, existingOpinions, latest, existingPrimary);

        var asset = merger.getUpdatedAssets().get("gcp_vminstance_q13");
        assertNotNull(asset);
        var defaultOpinionItem = asset.getOpinions()
            .getSourceAndServiceOpinion(defaultReportingSource, defaultReportingService);
        assertNotNull(defaultOpinionItem);
        var secondaryOpinionItem = asset.getOpinions()
            .getSourceAndServiceOpinion(defaultReportingSource, secondReportingService);
        assertNotNull(secondaryOpinionItem);
        assertEquals(defaultRawData, defaultOpinionItem.getData());
        assertEquals(secondRawData, secondaryOpinionItem.getData());
    }

    // Given an existing stub and opinion, an additional opinion from a different source is added
    @Test
    void differentSourceOpinionAdded() {
        var existingOpinions = createExistingOpinion(List.of("gcp_vminstance_q13"),
            defaultReportingSource, "gcp", defaultRawData);
        var latest = createLatest(List.of("q13"), secondReportingSource, "gcp", secondRawData);
        var existingPrimary = createExistingPrimary(List.of(), "gcp", null);

        var creator = getHelper(secondReportingSource, secondReportingService);
        var merger = MergeAssets.process(creator, existingOpinions, latest, existingPrimary);

        var asset = merger.getUpdatedAssets().get("gcp_vminstance_q13");
        assertNotNull(asset);
        var defaultOpinionItem = asset.getOpinions()
            .getSourceAndServiceOpinion(defaultReportingSource, defaultReportingService);
        var otherOpinionItem = asset.getOpinions()
            .getSourceAndServiceOpinion(secondReportingSource, secondReportingService);

        assertNotNull(defaultOpinionItem);
        assertNotNull(otherOpinionItem);
        assertEquals(defaultRawData, defaultOpinionItem.getData());
        assertEquals(secondRawData, otherOpinionItem.getData());
    }

    // Given both a stub & opinion asset, the opinion is missing from the latest.
    @Test
    void opinionWithStubIsMissing() {
        var existingOpinions = createExistingOpinion(List.of("gcp_vminstance_q13"),
            defaultReportingSource, "gcp", defaultRawData);
        var latest = createLatest(List.of(), defaultReportingSource, "gcp", null);
        var existingPrimary = createExistingPrimary(List.of("gcp_vminstance_q13"),
            "gcp", null);

        var creator = getHelper(defaultReportingSource, defaultReportingService
        );
        var merger = MergeAssets.process(creator, existingOpinions, latest, existingPrimary);

        assertEquals(Set.of(), merger.getNewAssets().keySet());
        assertEquals(Set.of(), merger.getMissingAssets().keySet());
        assertEquals(Set.of(), merger.getUpdatedAssets().keySet());

        var deletedOpinions = merger.getDeletedOpinionAssets();
        assertEquals(1, deletedOpinions.size());
        assertEquals("gcp_vminstance_q13", deletedOpinions.getFirst().getDocId());
        var deletedPrimaries = merger.getDeletedPrimaryAssets();
        assertEquals(1, deletedPrimaries.size());
        assertEquals("gcp_vminstance_q13", deletedPrimaries.getFirst().getDocId());
    }

    // Given both a stub & opinion asset, one of the two existing opinions from the same source is
    // missing from the latest.
    @Test
    void oneOpinionWithStubIsMissing() {
        var docId = "gcp_vminstance_q13";
        var existingOpinions = createExistingOpinion(List.of(docId),
            defaultReportingSource, "gcp", defaultRawData);
        addSecondOpinion(existingOpinions.get(docId), defaultReportingSource,
            secondReportingService,
            secondRawData);
        var latest = createLatest(List.of(), defaultReportingSource, "gcp", null);
        var existingPrimary = createExistingPrimary(List.of(docId),
            "gcp", null);

        var creator = getHelper(defaultReportingSource, defaultReportingService
        );
        var merger = MergeAssets.process(creator, existingOpinions, latest, existingPrimary);

        assertEquals(Set.of(), merger.getNewAssets().keySet());
        assertEquals(Set.of(), merger.getMissingAssets().keySet());
        assertTrue(merger.getDeletedOpinionAssets().isEmpty());
        assertTrue(merger.getDeletedPrimaryAssets().isEmpty());
        assertEquals(Set.of(docId), merger.getUpdatedAssets().keySet());

        var updatedAsset = merger.getUpdatedAssets().get(docId);
        assertNotNull(updatedAsset);

        // Ensure the proper opinion service remains
        var opinions = updatedAsset.getOpinions();
        var secondOpinion = opinions.getSourceAndServiceOpinion(defaultReportingSource, secondReportingService);
        assertNotNull(secondOpinion);
        assertNull(opinions.getSourceAndServiceOpinion(defaultReportingSource, defaultReportingService));
    }

    // Given both a stub & opinion asset, one of the two existing opinions from difference sources
    // is missing from the latest. This requires removing the missing opinion.
    @Test
    void oneOpinionSourceWithStubIsMissing() {
        var docId = "gcp_vminstance_q13";
        var existingOpinions = createExistingOpinion(List.of(docId),
            defaultReportingSource, "gcp", defaultRawData);
        addSecondOpinion(existingOpinions.get(docId), secondReportingSource, secondReportingService,
            secondRawData);
        var latest = createLatest(List.of(), secondReportingSource, "gcp", null);
        var existingPrimary = createExistingPrimary(List.of(docId),
            "gcp", null);

        var creator = getHelper(defaultReportingSource, defaultReportingService);
        var merger = MergeAssets.process(creator, existingOpinions, latest, existingPrimary);

        assertEquals(Set.of(), merger.getNewAssets().keySet());
        assertEquals(Set.of(), merger.getMissingAssets().keySet());
        assertTrue(merger.getDeletedOpinionAssets().isEmpty());
        assertTrue(merger.getDeletedPrimaryAssets().isEmpty());
        assertEquals(Set.of(docId), merger.getUpdatedAssets().keySet());

        var updatedAsset = merger.getUpdatedAssets().get(docId);
        assertNotNull(updatedAsset);

        // Ensure the proper opinion service remains
        var opinions = updatedAsset.getOpinions();
        var secondOpinion = opinions.getSourceAndServiceOpinion(defaultReportingSource, secondReportingService);
        assertNotNull(secondOpinion);
        assertNull(opinions.getSourceAndServiceOpinion(secondReportingSource, defaultReportingService));
    }

    // Given a stub and an opinion, a primary is added
    // The primary check here is that a full primary document is created
    @Test
    void primaryAddedExistingOpinion() {
        var stubbedPrimary = createExistingPrimary(List.of("gcp_vminstance_q13"), "gcp", null);
        var latest = createLatest(List.of("q13"), "gcp", defaultReportingSource, defaultRawData);

        var creator = getHelper(null, null);
        var merger = MergeAssets.process(creator, stubbedPrimary, latest, null);

        assertEquals(Set.of("gcp_vminstance_q13"), merger.getNewAssets().keySet());
        assertEquals(Set.of(), merger.getMissingAssets().keySet());
        assertEquals(Set.of(), merger.getUpdatedAssets().keySet());
        assertEquals(List.of(), merger.getDeletedOpinionAssets());
        assertEquals(List.of(), merger.getDeletedPrimaryAssets());

        var newAsset = merger.getNewAssets().get("gcp_vminstance_q13");
        assertNotNull(newAsset);
        assertNotNull(newAsset.getPrimaryProvider());
        assertNull(newAsset.getOpinions());
        assertEquals("name q13", newAsset.getResourceName());
    }

    // Given opinion mapping data and no existing opinion or primary data, create the opinion asset
    // as well as the stub primary asset.
    @Test
    void primaryFromOpinion() throws JsonProcessingException {
        var latest = List.of(JsonHelper.mapFromString(getOpinionMapperDocument()));
        var existingOpinions = createExistingOpinion(List.of(), defaultReportingSource, "gcp",
            null);

        var creator = getHelper("secondary", "vminstance");
        var merger = MergeAssets.process(creator, existingOpinions, latest, Map.of());

        assertEquals(Set.of(), merger.getMissingAssets().keySet());
        assertEquals(Set.of(), merger.getUpdatedAssets().keySet());
        assertEquals(List.of(), merger.getDeletedOpinionAssets());
        assertEquals(List.of(), merger.getDeletedPrimaryAssets());
        assertEquals(List.of(), merger.getDeletedOpinionAssets());
        assertEquals(1, merger.getNewAssets().size());
        assertEquals(1, merger.getNewPrimaryAssets().size());

        var opinionAsset = merger.getNewAssets().get("gcp_vminstance_134312");
        assertNotNull(opinionAsset);
        assertNotNull(opinionAsset.getDocId());
        assertNotNull(opinionAsset.getDocType());
        assertNotNull(opinionAsset.getOpinions());
        assertNull(opinionAsset.getAssetState());
        assertNull(opinionAsset.getSource());

        var primaryAsset = merger.getNewPrimaryAssets().get("gcp_vminstance_134312");
        assertNotNull(primaryAsset);
        assertNotNull(primaryAsset.getDocId());
        assertNotNull(primaryAsset.getDocType());
        assertNotNull(primaryAsset.getEntityType());
        assertNotNull(primaryAsset.getEntityTypeDisplayName());
        assertEquals(AssetState.SUSPICIOUS, primaryAsset.getAssetState());

        assertNull(primaryAsset.getPrimaryProvider());

        assertNotNull(primaryAsset.getResourceId());
        assertNotNull(primaryAsset.getResourceName());
        assertNotNull(primaryAsset.getRegion());
        assertNotNull(primaryAsset.getSource());
        assertNotNull(primaryAsset.getLegacySourceDisplayName());
        assertNotNull(primaryAsset.getAccountId());
        assertNotNull(primaryAsset.getAccountName());

        assertNotNull(primaryAsset.getFirstDiscoveryDate());
        assertNotNull(primaryAsset.getLoadDate());
        assertNotNull(primaryAsset.getLastScanDate());

        assertTrue(primaryAsset.getIsLatest());
        assertTrue(primaryAsset.getIsEntity());

        assertNull(primaryAsset.getOpinions());
        assertNull(primaryAsset.getPrimaryProvider());
        assertTrue(primaryAsset.getAdditionalProperties().isEmpty());
    }

    private String getOpinionMapperDocument() {
        return """
            {
                "rawData": "{\\"flavor\\":\\"licorice\\"}",
                "_lastScanDate": "2024-10-15 19:12:55+0000",
                "id": "134312",
                "resource_name": "some_vm",
                "account_id": "central-run-3433",
                "account_name": "main",
                "projectId": "central-run-3433",
                "source": "secondary",
                "reporting_source": "gcp",
                "source_display_name": "GCP",
                "region": "us-central1-a",
                "_entityType": "vminstance",
                "_entityTypeDisplayName": "VM"
            }""".trim();
    }

}
