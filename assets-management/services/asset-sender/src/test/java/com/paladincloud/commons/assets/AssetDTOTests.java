package com.paladincloud.commons.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.paladincloud.common.AssetDocumentFields;
import com.paladincloud.common.assets.AssetDTO;
import com.paladincloud.common.search.ElasticQueryAssetResponse;
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
    void elasticResponseDeserialized() throws JsonProcessingException {
        var sampleJson = getElasticResponse();
//        var deserialized = JsonHelper.objectMapper.readValue(sampleJson, ElasticQueryAssetResponse.class);
        var deserialized = JsonHelper.fromString(ElasticQueryAssetResponse.class, sampleJson);
        assertNotNull(deserialized);
        assertNotNull(deserialized.hits);
        assertNotNull(deserialized.hits.hits);
        assertFalse(deserialized.hits.hits.isEmpty());
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
                "tags": {"environment": "bar" }
            }
        """.trim();
    }

    private String getElasticResponse() {
        return """
            {
              "took": 538,
              "timed_out": false,
              "_shards": {
                "total": 1,
                "successful": 1,
                "skipped": 0,
                "failed": 0
              },
              "hits": {
                "total": {
                  "value": 4,
                  "relation": "eq"
                },
                "max_score": null,
                "hits": [
                  {
                    "_index": "gcp_vminstance",
                    "_id": "central-run-349616_us-central1-a_3228267340273394036",
                    "_score": null,
                    "_source": {
                      "owner": true,
                      "_docid": "central-run-349616_us-central1-a_3228267340273394036",
                      "docType": "vminstance",
                      "_cspm_source": null,
                      "_reporting_source": "gcp",
                      "_cloudType": "gcp",
                      "latest": true,
                      "_entity": "true",
                      "_entitytype": "vminstance",
                      "_loaddate": "2024-09-27 23:14:00+0000",
                      "name": "paladincloud-demo-vm",
                      "_resourcename": "3228267340273394036",
                      "_resourceid": "3228267340273394036",
                      "tags": {
                        "application": "coffeeapp",
                        "environment": "test"
                      },
                      "sourceDisplayName": "GCP",
                      "primaryProvider": "{\\"auto_restart\\":true,\\"can_ip_forward\\":false,\\"confidential_computing\\":false,\\"description\\":\\"\\",\\"disks\\":[{\\"id\\":\\"0\\",\\"projectId\\":\\"central-run-349616\\",\\"projectName\\":\\"Paladin Cloud\\",\\"name\\":\\"paladincloud-demo-vm\\",\\"sizeInGb\\":10,\\"type\\":\\"PERSISTENT\\",\\"autoDelete\\":true,\\"hasSha256\\":false,\\"hasKMSKeyName\\":false,\\"labels\\":null,\\"region\\":\\"\\"}],\\"emails\\":[\\"344106022091-compute@developer.gserviceaccount.com\\"],\\"id\\":3228267340273394036,\\"item_interfaces\\":[{\\"key\\":\\"enable-oslogin\\",\\"value\\":\\"true\\"}],\\"labels\\":{\\"application\\":\\"coffeeapp\\",\\"environment\\":\\"test\\"},\\"machine_type\\":\\"https://www.googleapis.com/compute/v1/projects/central-run-349616/zones/us-central1-a/machineTypes/e2-medium\\",\\"name\\":\\"paladincloud-demo-vm\\",\\"network_interfaces\\":[{\\"id\\":\\"10.128.0.32\\",\\"name\\":\\"nic0\\",\\"network\\":\\"https://www.googleapis.com/compute/v1/projects/central-run-349616/global/networks/default\\",\\"accessConfig\\":[{\\"id\\":\\"External NAT\\",\\"name\\":\\"External NAT\\",\\"natIp\\":\\"34.31.240.178\\",\\"projectName\\":\\"Paladin Cloud\\"}]}],\\"on_host_maintainence\\":\\"MIGRATE\\",\\"project_id\\":\\"central-run-349616\\",\\"project_name\\":\\"Paladin Cloud\\",\\"project_number\\":344106022091,\\"region\\":\\"us-central1-a\\",\\"scopes\\":[\\"https://www.googleapis.com/auth/devstorage.read_only\\",\\"https://www.googleapis.com/auth/logging.write\\",\\"https://www.googleapis.com/auth/monitoring.write\\",\\"https://www.googleapis.com/auth/servicecontrol\\",\\"https://www.googleapis.com/auth/service.management.readonly\\",\\"https://www.googleapis.com/auth/trace.append\\"],\\"service_accounts\\":[{\\"email\\":\\"344106022091-compute@developer.gserviceaccount.com\\",\\"emailBytes\\":{},\\"scopeList\\":[\\"https://www.googleapis.com/auth/devstorage.read_only\\",\\"https://www.googleapis.com/auth/logging.write\\",\\"https://www.googleapis.com/auth/monitoring.write\\",\\"https://www.googleapis.com/auth/servicecontrol\\",\\"https://www.googleapis.com/auth/service.management.readonly\\",\\"https://www.googleapis.com/auth/trace.append\\"]}],\\"shielded_instance_config\\":{\\"enableVtpm\\":true,\\"enableIntegrityMonitoring\\":true},\\"status\\":\\"RUNNING\\"}",
                      "assetIdDisplayName": null,
                      "targettypedisplayname": "VM",
                      "targetTypeDisplayName": "VM",
                      "accountid": "central-run-349616",
                      "accountname": "Paladin Cloud",
                      "discoverydate": "2024-09-27 22:28:00+0000",
                      "firstdiscoveredon": "2024-09-27 22:28:00+0000",
                      "tags.Environment": "test",
                      "tags.Application": "coffeeapp",
                      "vminstance_relations": "vminstance"
                    },
                    "sort": [
                      "2024-09-27 23:14:00+0000"
                    ]
                  }
                ]
              }
            }            """.trim();
    }
}
