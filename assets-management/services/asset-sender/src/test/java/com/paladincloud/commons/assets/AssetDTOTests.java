package com.paladincloud.commons.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.paladincloud.common.AssetDocumentFields;
import com.paladincloud.common.assets.AssetDTO;
import com.paladincloud.common.util.JsonHelper;
import com.paladincloud.common.util.TimeHelper;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

public class AssetDTOTests {

    /**
     * Some AssetDTO fields are stored as strings though they're booleans and dates. This validates
     * they're stored in JSON as the correct type for backward compatibility.
     */
    @Test
    void specialTypesUsedForSomeFields() throws JsonProcessingException {
        var dateTime = ZonedDateTime.now();
        var original = createAsset(dateTime);
        var asJson = JsonHelper.objectMapper.writeValueAsString(original);

        // Deserialize to a map to avoid using the Json directives - the directives are being
        // verified here
        var asMap = JsonHelper.mapFromString(asJson);
        assertNotNull(asMap);

        // These are serialized as strings even though they're booleans.
        assertEquals("true", asMap.get(AssetDocumentFields.ENTITY));

        // These are serialized as strings and exposed as ZonedDateTime
        assertEquals(TimeHelper.formatZeroSeconds(dateTime),
            asMap.get(AssetDocumentFields.LOAD_DATE));
        assertEquals(TimeHelper.formatZeroSeconds(dateTime),
            asMap.get(AssetDocumentFields.FIRST_DISCOVERED));
        assertEquals(TimeHelper.formatZeroSeconds(dateTime),
            asMap.get(AssetDocumentFields.DISCOVERY_DATE));
    }

    @Test
    void existingFormatWorksEndToEnd() throws JsonProcessingException {
        var sampleJson = getSampleSerializedDocument();

        // Get a round-trip from the string to AssetDTO back to string for validation
        var deserialized = JsonHelper.objectMapper.readValue(sampleJson, AssetDTO.class);
        assertNotNull(deserialized);
        var deserializedJson = JsonHelper.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(deserialized);
        assertNotNull(deserializedJson);

        // Deserialize to a map to avoid using the Json directives - it's the directives being
        // verified here
        var sampleAsMap = JsonHelper.mapFromString(sampleJson);
        assertNotNull(sampleAsMap);
        var deserializedAsMap = JsonHelper.mapFromString(deserializedJson);
        assertNotNull(deserializedAsMap);

        // Ensure each value in the sample exists in the serialized/deserialized instance
        sampleAsMap.forEach((key, value) -> {
            assertTrue(deserializedAsMap.containsKey(key), key);
            assertEquals(value, deserializedAsMap.get(key), STR."\{key} value differs. actual=\{value} expected=\{deserializedAsMap.get(key)}");
        });
    }

    private AssetDTO createAsset(ZonedDateTime dateTime) {
        var dto = new AssetDTO();
        dto.setDocId("1");
        dto.setName("name");
        dto.setLatest(true);
        dto.setEntity(true);
        dto.setLoadDate(dateTime);
        dto.setDiscoveryDate(dateTime);
        dto.setFirstDiscoveryDate(dateTime);
        return dto;
    }

    private String getSampleSerializedDocument() {
        return """
            {
                "owner": true,
                "_docid": "us-central",
                "docType": "ec2",
                "_cspm_source": "Paladin Cloud",
                "_reporting_source": "gcp",
                "_cloudType": "gcp",
                "latest": true,
                "_entity": "true",
                "_entitytype": "ec2",
                "name": "instance-abc",
                "_resourcename": "17",
                "_resourceid": "17",
                "sourceDisplayName": "GCP",
                "assetIdDisplayName": null,
                "targettypedisplayname": "ec2",
                "accountid": "xyz",
                "accountname": null,
                "discoverydate": "2024-09-05 14:57:00+0000",
                "firstdiscoveredon": "2024-09-05 14:57:00+0000",
                "ec2_relations": "ec2",
                "tags.foo": "bar"
            }
        """.trim();
    }
}
