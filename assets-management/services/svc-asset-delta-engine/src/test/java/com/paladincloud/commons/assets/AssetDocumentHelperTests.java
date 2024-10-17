package com.paladincloud.commons.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.paladincloud.common.AssetDocumentFields;
import com.paladincloud.common.assets.AssetDocumentHelper;
import com.paladincloud.common.assets.AssetState;
import com.paladincloud.common.util.JsonHelper;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * These tests are to ensure the conversion from a mapped entity to an AssetDTO is correct
 */
public class AssetDocumentHelperTests {

    static private AssetDocumentHelper getHelper(String dataSource, String idField, String resourceNameField) {
        return AssetDocumentHelper.builder()
            .loadDate(ZonedDateTime.now())
            .idField(idField)
            .docIdFields(List.of("accountid", "region", idField))
            .dataSource(dataSource)
            .displayName("ec2")
            .tags(List.of())
            .type("ec2")
            .accountIdToNameFn( (_) -> null)
            .assetState(AssetState.MANAGED)
            .resourceNameField(resourceNameField)
            .build();
    }

    @Test
    void dtoIsPopulatedFromV2Mapper() throws JsonProcessingException {
        AssetDocumentHelper helper = getHelper("gcp", "resource_id", "resource_name");
        var mapperData = JsonHelper.mapFromString(getV2PrimaryMapperDocument());
        var dto = helper.createFrom(mapperData);

        var dtoAsMap = JsonHelper.mapFromString(JsonHelper.objectMapper.writeValueAsString(dto));
        var expectedMap = JsonHelper.mapFromString(getSamplePrimaryAssetDocument());

        assertNotNull(dto);
        assertNotNull(dtoAsMap.get(AssetDocumentFields.LOAD_DATE));
        assertFalse(dtoAsMap.get(AssetDocumentFields.LOAD_DATE).toString().isBlank());
        assertNotNull(dtoAsMap.get(AssetDocumentFields.PRIMARY_PROVIDER));
        assertEquals(mapperData.get("rawData"), dtoAsMap.get(AssetDocumentFields.PRIMARY_PROVIDER));

        // Ensure each value in the sample asset exists in the serialized/deserialized instance
        expectedMap.forEach((key, value) -> {
            assertTrue(dtoAsMap.containsKey(key), key);
            assertEquals(value, dtoAsMap.get(key),
                STR."\{key} value differs. expected=\{value} actual=\{dtoAsMap.get(key)}");
        });
    }

    @Test
    void primaryDtoIsFullyPopulatedFromLegacyMapper() throws JsonProcessingException {
        AssetDocumentHelper helper = getHelper("gcp", "_resource_id", "_resource_name");
        var mapperData = JsonHelper.mapFromString(getLegacyPrimaryMapperDocument());

        var dto = helper.createFrom(mapperData);

        var dtoAsMap = JsonHelper.mapFromString(JsonHelper.objectMapper.writeValueAsString(dto));
        var expectedMap = JsonHelper.mapFromString(getSamplePrimaryAssetDocument());

        assertNotNull(dto);
        assertNotNull(dtoAsMap.get(AssetDocumentFields.LOAD_DATE));
        assertFalse(dtoAsMap.get(AssetDocumentFields.LOAD_DATE).toString().isBlank());
        assertNotNull(dtoAsMap.get(AssetDocumentFields.PRIMARY_PROVIDER));
        assertEquals(mapperData.get("rawData"), dtoAsMap.get(AssetDocumentFields.PRIMARY_PROVIDER));

        // Ensure each value in the sample asset exists in the serialized/deserialized instance
        expectedMap.forEach((key, value) -> {
                assertTrue(dtoAsMap.containsKey(key), key);
                assertEquals(value, dtoAsMap.get(key),
                    STR."'\{key}' value differs. expected=\{value} actual=\{dtoAsMap.get(key)}");
        });
    }

    @Test
    void dtoIsUpdated() throws JsonProcessingException {
        var mappedAsMap = JsonHelper.mapFromString(getLegacyPrimaryMapperDocument());
        var dto = getHelper("gcp", "_resource_id", null).createFrom(mappedAsMap);

        mappedAsMap.put(AssetDocumentFields.ACCOUNT_NAME, "new account name");

        AssetDocumentHelper helper = getHelper("gcp", "_resource_id", null);
        helper.updateFrom(mappedAsMap, dto);

        assertEquals("new account name", dto.getLegacyAccountName());
        assertTrue(dto.isLatest());
    }

    /**
     * Ensure an updated DTO receives new fields - this is the upgrade strategy for existing
     * documents.
     */
    @Test
    void updatedDtoHasNewFields() throws JsonProcessingException {
        var mappedAsMap = JsonHelper.mapFromString(getLegacyPrimaryMapperDocument());
        var dto = getHelper("gcp", "_resource_id", null).createFrom(mappedAsMap);

        AssetDocumentHelper helper = getHelper("gcp", "_resource_id", null);
        helper.updateFrom(mappedAsMap, dto);
    }

    @Test
    void opinionDtoIsFullyPopulated() throws JsonProcessingException {
        AssetDocumentHelper helper = getHelper("secondary", "_resourceid", null);
        var mappedAsMap = JsonHelper.mapFromString(getSampleOpinionDocument());
        var dto = helper.createFrom(mappedAsMap);
        assertNotNull(dto);

//        assertEquals("secondary", dto.getReportingSource());
    }

    private String getV2PrimaryMapperDocument() {
        return """
            {
                "rawData": "{\\"auto_restart\\":true,\\"can_ip_forward\\":false,\\"confidential_computing\\":false,\\"description\\":\\"\\",\\"disks\\":[{\\"id\\":\\"0\\",\\"projectId\\":\\"xyz\\",\\"projectName\\":\\"Project\\",\\"name\\":\\"instance-abc\\",\\"sizeInGb\\":50,\\"type\\":\\"PERSISTENT\\",\\"autoDelete\\":true,\\"hasSha256\\":false,\\"hasKMSKeyName\\":false,\\"labels\\":null,\\"region\\":\\"\\"}],\\"emails\\":[\\"fubar@developer.gserviceaccount.com\\"],\\"id\\":17,\\"item_interfaces\\":[{\\"key\\":\\"enable-oslogin\\",\\"value\\":\\"true\\"}],\\"labels\\":{},\\"machine_type\\":\\"https://www.googleapis.com/compute/v1/projects/xyz/zones/z/machineTypes/e2-standard-2\\",\\"name\\":\\"instance-abc\\",\\"network_interfaces\\":[{\\"id\\":\\"100.128.100.189\\",\\"name\\":\\"nic0\\",\\"network\\":\\"https://www.googleapis.com/compute/v1/projects/xyz/global/networks/default\\",\\"accessConfig\\":[{\\"id\\":\\"External NAT\\",\\"name\\":\\"External NAT\\",\\"natIp\\":null,\\"projectName\\":\\"Project\\"}]}],\\"on_host_maintainence\\":\\"MIGRATE\\",\\"project_id\\":\\"xyz\\",\\"project_name\\":\\"Project\\",\\"project_number\\":344106022091,\\"region\\":\\"r\\",\\"scopes\\":[\\"https://www.googleapis.com/auth/devstorage.read_only\\",\\"https://www.googleapis.com/auth/logging.write\\",\\"https://www.googleapis.com/auth/monitoring.write\\",\\"https://www.googleapis.com/auth/servicecontrol\\",\\"https://www.googleapis.com/auth/service.management.readonly\\",\\"https://www.googleapis.com/auth/trace.append\\"],\\"service_accounts\\":[{\\"email\\":\\"fubar@developer.gserviceaccount.com\\",\\"emailBytes\\":{},\\"scopeList\\":[\\"https://www.googleapis.com/auth/devstorage.read_only\\",\\"https://www.googleapis.com/auth/logging.write\\",\\"https://www.googleapis.com/auth/monitoring.write\\",\\"https://www.googleapis.com/auth/servicecontrol\\",\\"https://www.googleapis.com/auth/service.management.readonly\\",\\"https://www.googleapis.com/auth/trace.append\\"]}],\\"shielded_instance_config\\":{\\"enableVtpm\\":true,\\"enableIntegrityMonitoring\\":true},\\"status\\":\\"TERMINATED\\"}",
                "_lastScanDate": "2024-09-05 14:57:00+0000",
                "resource_id": "17",
                "resource_name": "instance-abc",
                "account_id": "abc",
                "source": "gcp",
                "source_display_name": "GCP",
                "_entityType": "vminstance",
                "_entityTypeDisplayName": "VM",
                "region": "us-central",
                "reporting_source": "gcp",
                "tags": { "environment": "test" },
                "projectId": "xyz"
            }""".trim();
    }

    private String getLegacyPrimaryMapperDocument() {
        return """
            {
                "rawData": "{\\"auto_restart\\":true,\\"can_ip_forward\\":false,\\"confidential_computing\\":false,\\"description\\":\\"\\",\\"disks\\":[{\\"id\\":\\"0\\",\\"projectId\\":\\"xyz\\",\\"projectName\\":\\"Project\\",\\"name\\":\\"instance-abc\\",\\"sizeInGb\\":50,\\"type\\":\\"PERSISTENT\\",\\"autoDelete\\":true,\\"hasSha256\\":false,\\"hasKMSKeyName\\":false,\\"labels\\":null,\\"region\\":\\"\\"}],\\"emails\\":[\\"fubar@developer.gserviceaccount.com\\"],\\"id\\":17,\\"item_interfaces\\":[{\\"key\\":\\"enable-oslogin\\",\\"value\\":\\"true\\"}],\\"labels\\":{},\\"machine_type\\":\\"https://www.googleapis.com/compute/v1/projects/xyz/zones/z/machineTypes/e2-standard-2\\",\\"name\\":\\"instance-abc\\",\\"network_interfaces\\":[{\\"id\\":\\"100.128.100.189\\",\\"name\\":\\"nic0\\",\\"network\\":\\"https://www.googleapis.com/compute/v1/projects/xyz/global/networks/default\\",\\"accessConfig\\":[{\\"id\\":\\"External NAT\\",\\"name\\":\\"External NAT\\",\\"natIp\\":null,\\"projectName\\":\\"Project\\"}]}],\\"on_host_maintainence\\":\\"MIGRATE\\",\\"project_id\\":\\"xyz\\",\\"project_name\\":\\"Project\\",\\"project_number\\":344106022091,\\"region\\":\\"r\\",\\"scopes\\":[\\"https://www.googleapis.com/auth/devstorage.read_only\\",\\"https://www.googleapis.com/auth/logging.write\\",\\"https://www.googleapis.com/auth/monitoring.write\\",\\"https://www.googleapis.com/auth/servicecontrol\\",\\"https://www.googleapis.com/auth/service.management.readonly\\",\\"https://www.googleapis.com/auth/trace.append\\"],\\"service_accounts\\":[{\\"email\\":\\"fubar@developer.gserviceaccount.com\\",\\"emailBytes\\":{},\\"scopeList\\":[\\"https://www.googleapis.com/auth/devstorage.read_only\\",\\"https://www.googleapis.com/auth/logging.write\\",\\"https://www.googleapis.com/auth/monitoring.write\\",\\"https://www.googleapis.com/auth/servicecontrol\\",\\"https://www.googleapis.com/auth/service.management.readonly\\",\\"https://www.googleapis.com/auth/trace.append\\"]}],\\"shielded_instance_config\\":{\\"enableVtpm\\":true,\\"enableIntegrityMonitoring\\":true},\\"status\\":\\"TERMINATED\\"}",
                "autoRestart": true,
                "sourceDisplayName": "GCP",
                "serviceAccounts": [
                    {
                        "email": "fubar@developer.gserviceaccount.com",
                        "emailBytes": {},
                        "scopeList": [
                            "https://www.googleapis.com/auth/devstorage.read_only",
                            "https://www.googleapis.com/auth/logging.write",
                            "https://www.googleapis.com/auth/monitoring.write",
                            "https://www.googleapis.com/auth/servicecontrol",
                            "https://www.googleapis.com/auth/service.management.readonly",
                            "https://www.googleapis.com/auth/trace.append"
                        ]
                    }
                ],
                "disks": [
                    {
                        "id": "0",
                        "projectId": "xyz",
                        "projectName": "Project",
                        "name": "instance-abc",
                        "sizeInGb": 50,
                        "type": "PERSISTENT",
                        "autoDelete": true,
                        "hasSha256": false,
                        "hasKmsKeyName": false,
                        "labels": null,
                        "region": ""
                    }
                ],
                "shieldedInstanceConfig": {
                    "enableVtpm": true,
                    "enableIntegrityMonitoring": true
                },
                "emailList": [
                    "fubar@developer.gserviceaccount.com"
                ],
                "discoverydate": "2024-09-05 14:57:00+0000",
                "projectNumber": 19,
                "onHostMaintainence": "MIGRATE",
                "description": "",
                "confidentialComputing": false,
                "canIPForward": false,
                "_cloudType": "gcp",
                "reporting_source": "gcp",
                "tags": { "environment": "test" },
                "networkInterfaces": [
                    {
                        "id": "10.128.0.89",
                        "name": "nic0",
                        "network": "https://www.googleapis.com/compute/v1/projects/abc/global/networks/default",
                        "accessConfigs": [
                            {
                                "id": "External NAT",
                                "name": "External NAT",
                                "natIP": null,
                                "projectName": "Project"
                            }
                        ]
                    }
                ],
                "name": "instance-abc",
                "_resource_id": "17",
                "_resource_name": "instance-abc",
                "region": "us-central",
                "projectId": "xyz",
                "items": [
                    {
                        "key": "enable-oslogin",
                        "value": "true"
                    }
                ],
                "machineType": "https://www.googleapis.com/compute/v1/projects/central-run-349616/zones/us-central1-f/machineTypes/e2-standard-2",
                "scopesList": [
                    "https://www.googleapis.com/auth/devstorage.read_only",
                    "https://www.googleapis.com/auth/logging.write",
                    "https://www.googleapis.com/auth/monitoring.write",
                    "https://www.googleapis.com/auth/servicecontrol",
                    "https://www.googleapis.com/auth/service.management.readonly",
                    "https://www.googleapis.com/auth/trace.append"
                ],
                "status": "TERMINATED"

            }""".trim();
    }

    private String getSampleOpinionDocument() {
        return """
            {
                "_resourceid": "i-76",
                "_resourcename": "ABD-DEF",
                "_cloudType": "aws",
                "reporting_source": "aws",
                "_entitytype": "server",
                "lastSeenTime": "2024-07-19T05:23:44Z",
                "osBuild": "20348",
                "serialNumber": "007",
                "publicIpAddress": "74.313.911.257",
                "discoverydate": "2024-07-25 16:32:26+0000",
                "systemProductName": "HVM domU",
                "externalId": "ext-7e86",
                "accountid": "64",
                "externalAccountId": "ext-7e86-3924",
                "osVersion": "Windows Server 2022",
                "provisionStatus": "Provisioned",
                "firstSeenTime": "2024-05-07T10:15:19Z",
                "osType": "Windows",
                "systemManufacturer": "Xen",
                "reducedFunctionalityMode": "no",
                "region": "us-2",
                "status": "normal"
            }""".trim();
    }

    /**
     * This is the expected DTO, converted to a map converted to a JSON string
     *
     * The validation checks each top-level field; in order to minimize problems, some fields, such
     * as load date, are removed from this sample in order to allow simple comparison
     */
    private String getSamplePrimaryAssetDocument() {
        return """
            {
                "_docid": "us-central_17",
                "docType": "ec2",
                "_cloudType": "gcp",
                "latest": true,
                "_entity": "true",
                "_entitytype": "ec2",
                "name": "instance-abc",
                "_resourcename": "instance-abc",
                "_resourceid": "17",
                "sourceDisplayName": "GCP",
                "assetIdDisplayName": null,
                "targettypedisplayname": "ec2",
                "accountid": "xyz",
                "accountname": null,
                "discoverydate": "2024-09-05 14:57:00+0000",
                "firstdiscoveredon": "2024-09-05 14:57:00+0000",
                "ec2_relations": "ec2"
            }
        """.trim();
    }
}
