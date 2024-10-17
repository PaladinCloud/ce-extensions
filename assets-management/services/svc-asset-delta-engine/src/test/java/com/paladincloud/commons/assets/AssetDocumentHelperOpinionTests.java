package com.paladincloud.commons.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.paladincloud.common.AssetDocumentFields;
import com.paladincloud.common.assets.AssetDocumentHelper;
import com.paladincloud.common.assets.AssetState;
import com.paladincloud.common.util.JsonHelper;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class AssetDocumentHelperOpinionTests {

    static private AssetDocumentHelper getHelper(String dataSource, String idField,
        String resourceNameField, AssetState assetState) {
        return AssetDocumentHelper.builder()
            .loadDate(ZonedDateTime.now())
            .idField(idField)
            .docIdFields(List.of("projectId", "region", idField))
            .dataSource(dataSource)
            .displayName("vm instance")
            .tags(List.of())
            .type("vminstance")
            .accountIdToNameFn((_) -> null)
            .assetState(assetState)
            .resourceNameField(resourceNameField)
            .opinionSource("assets")
            .build();
    }

    @Test
    void opinionPopulatedFromMapper() throws JsonProcessingException {
        var mapperData = JsonHelper.mapFromString(getOpinionMapperDocument());
        AssetDocumentHelper helper = getHelper("gcp", "resource_id", "resource_name",
            AssetState.MANAGED);
        var dto = helper.createFrom(mapperData);

        var dtoAsMap = JsonHelper.mapFromString(JsonHelper.objectMapper.writeValueAsString(dto));
        var expectedMap = JsonHelper.mapFromString(getOpinionAssetDocument());

        assertNotNull(dto);
        assertNotNull(dto.getDocId());
        assertNotNull(dto.getDocType());
        assertNotNull(dto.getOpinions());

        assertNull(dto.getPrimaryProvider());
        assertNull(dto.getRegion());

        // Validate the opinion is stored properly
        var opinions = dto.getOpinions();
        Map<String, String> opinionDetails = opinions.get("secondary");
        assertNotNull(opinionDetails);
        assertEquals(mapperData.get("rawData"), opinionDetails.get("assets"));

        // Ensure each value in the sample asset exists in the serialized/deserialized instance
        expectedMap.forEach((key, value) -> {
            assertTrue(dtoAsMap.containsKey(key), key);
            assertEquals(value, dtoAsMap.get(key),
                STR."\{key} value differs. expected=\{value} actual=\{dtoAsMap.get(key)}");
        });
    }

    @Test
    void opinionUpdated() throws JsonProcessingException {
        var mapperData = JsonHelper.mapFromString(getOpinionMapperDocument());

        // Get the existing  document (the one from OpenSearch, for instance)
        AssetDocumentHelper helper = getHelper("gcp", "resource_id", "resource_name",
            AssetState.MANAGED);
        var dto = helper.createFrom(mapperData);

        var newRawData = "{\"temperature\":\"chilly\"}";
        mapperData.put("rawData", newRawData);
        helper.updateFrom(mapperData, dto);

        var opinions = dto.getOpinions();
        Map<String, String> opinionDetails = opinions.get("secondary");
        assertNotNull(opinionDetails);
        assertEquals(newRawData, opinionDetails.get("assets"));
    }

    // Given an opinion, create a primary asset. This is the version of the asset stored in the
    // primary index when it's not present but an opinion reports the asset
    @Test
    void primaryFromOpinion() throws JsonProcessingException {
        var mapperData = JsonHelper.mapFromString(getOpinionMapperDocument());
        AssetDocumentHelper helper = getHelper("gcp", "resource_id", "resource_name",
            AssetState.SUSPICIOUS);
        var dto = helper.createFrom(mapperData);

        assertNotNull(dto);
        assertNotNull(dto.getDocId());
        assertNotNull(dto.getDocType());
        assertNotNull(dto.getEntityType());
        assertNotNull(dto.getEntityTypeDisplayName());
        assertEquals(AssetState.SUSPICIOUS, dto.getAssetState());

        assertNotNull(dto.getResourceId());
        assertNotNull(dto.getResourceName());
        assertNotNull(dto.getRegion());
        assertNotNull(dto.getSource());
        assertNotNull(dto.getLegacySourceDisplayName());
        assertNotNull(dto.getAccountId());
        assertNotNull(dto.getAccountName());

        assertNotNull(dto.getFirstDiscoveryDate());
        assertNotNull(dto.getLoadDate());
        assertNotNull(dto.getLastScanDate());

        assertTrue(dto.isLatest());
        assertTrue(dto.isEntity());

        assertNull(dto.getOpinions());
        assertNull(dto.getPrimaryProvider());
        assertTrue(dto.getAdditionalProperties().isEmpty());
    }

    @Test
    public void updatePrimaryFromOpinion() throws JsonProcessingException {
        var mapperData = JsonHelper.mapFromString(getOpinionMapperDocument());
        AssetDocumentHelper helper = getHelper("gcp", "resource_id", "resource_name",
            AssetState.SUSPICIOUS);
        var dto = helper.createFrom(mapperData);

        mapperData.put(AssetDocumentFields.RESOURCE_NAME, "new name");
        helper.updateFrom(mapperData, dto);

        assertEquals("new name", dto.getResourceName());
    }

    private String getOpinionMapperDocument() {
        return """
            {
                "rawData": "{\\"flavor\\":\\"licorice\\"}",
                "_lastScanDate": "2024-10-15 19:12:55+0000",
                "resource_id": "134312",
                "resource_name": "some_vm",
                "account_id": "central-run-3433",
                "account_name": "main",
                "projectId": "central-run-3433",
                "source": "gcp",
                "reporting_source": "secondary",
                "source_display_name": "GCP",
                "region": "us-central1-a",
                "_entityType": "vminstance",
                "_entityTypeDisplayName": "VM"
            }""".trim();
    }

    /**
     * This is the expected DTO, converted to a map converted to a JSON string
     * <p>
     * The validation checks each top-level field; in order to minimize problems, some fields, such
     * as load date, are removed from this sample in order to allow simple comparison
     */
    private String getOpinionAssetDocument() {
        return """
                {
                    "_docId": "central-run-3433_us-central1-a_134312",
                    "_docType": "vminstance",
                    "opinions": {
                        "secondary": {
                            "assets": "{\\"flavor\\":\\"licorice\\"}"
                        }
                    }
                }
            """.trim();
    }
}
