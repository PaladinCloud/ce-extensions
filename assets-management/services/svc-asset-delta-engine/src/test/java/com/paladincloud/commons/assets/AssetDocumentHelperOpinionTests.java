package com.paladincloud.commons.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.paladincloud.common.assets.AssetDocumentHelper;
import com.paladincloud.common.assets.AssetState;
import com.paladincloud.common.util.JsonHelper;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class AssetDocumentHelperOpinionTests {

    static private AssetDocumentHelper getHelper() {
        return AssetDocumentHelper.builder()
            .loadDate(ZonedDateTime.now())
            .idField("resource_id")
            .docIdFields(List.of("projectId", "region", "resource_id"))
            .dataSource("gcp")
            .displayName("vm instance")
            .tags(List.of())
            .type("vminstance")
            .accountIdToNameFn((_) -> null)
            .assetState(AssetState.MANAGED)
            .resourceNameField("resource_name")
            .reportingSource("secondary")
            .reportingSourceService("assets")
            .reportingSourceServiceDisplayName("Assets API")
            .build();
    }

    @Test
    void opinionPopulatedFromMapper() throws JsonProcessingException {
        var mapperData = JsonHelper.mapFromString(getOpinionMapperDocument());
        AssetDocumentHelper helper = getHelper();
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
        var opinionItem = dto.getOpinions().getSourceAndServiceOpinion("secondary", "assets");
        assertNotNull(opinionItem);
        assertEquals(mapperData.get("rawData"), opinionItem.getData());
        assertNotNull(opinionItem.getFirstScanDate());
        assertNotNull(opinionItem.getLastScanDate());

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
        AssetDocumentHelper helper = getHelper();
        var dto = helper.createFrom(mapperData);

        var newRawData = "{\"temperature\":\"chilly\"}";
        mapperData.put("rawData", newRawData);
        helper.updateFrom(mapperData, dto);

        var opinionItem = dto.getOpinions().getSourceAndServiceOpinion("secondary", "assets");
        assertNotNull(opinionItem);
        assertEquals(newRawData, opinionItem.getData());
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
                "source_display_name": "GCP",
                "region": "us-central1-a",
                "_entityType": "vminstance",
                "_entityTypeDisplayName": "VM",
                "_first_scan_date": "2024-09-05T14:57:00Z",
                "_last_scan_date": "2024-10-31T18:33:19Z",
                "_deep_link": "https://fubar.com"
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
                    "_docId": "gcp_vminstance_central-run-3433_us-central1-a_134312",
                    "_docType": "vminstance",
                    "opinions": {
                        "secondary": {
                            "assets": {
                                "data": "{\\"flavor\\":\\"licorice\\"}",
                                "firstScanDate": "2024-09-05 14:57:00+0000",
                                "lastScanDate": "2024-10-31 18:33:00+0000",
                                "serviceName": "Assets API",
                                "deepLink": "https://fubar.com"
                            }
                        }
                    }
                }
            """.trim();
    }
}
