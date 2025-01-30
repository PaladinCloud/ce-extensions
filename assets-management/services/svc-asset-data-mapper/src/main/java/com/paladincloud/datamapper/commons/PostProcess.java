package com.paladincloud.datamapper.commons;

import com.amazonaws.util.StringUtils;
import lombok.SneakyThrows;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

import static com.paladincloud.datamapper.commons.MapperConstants.DOC_ID;

public class PostProcess {

    private static final String CROWDSTRIKE_VULNINFO_DEEP_LINK_URL = "https://falcon.%s.crowdstrike.com/spotlight/vulnerabilities/group-by/vulnerability-id?filter=status%%3A!%%27closed%%27%%2Bapp.product_name_version_normalized%%3A%%27%s%%27%%2Bcve.severity%%3A%%27%s%%27%%2Bhost_info.hostname%%3A%%27%s%%27";
    private static final String CROWDSTRIKE_DETECTION_DEEP_LINK_PATH = "/activity-v2/detections?filter=status%%3A!%%27closed%%27%%2Bseverity%%3A%%27%s%%27%%2Bfilename%%3A%%27%s%%27%%2Bagent_id%%3A%%27%s%%27";
    private static final String MITRE_TECHNIQUE_URL = "https://attack.mitre.org/techniques/";
    private static final String ACTIVITY_V2_PATH = "/activity-v2";
    public static final String NIST_VULN_DETAILS_URL = "https://nvd.nist.gov/vuln/detail/";
    private static final String TENABLE_VULN_DIRECT_URL_TEMPLATE = "https://cloud.tenable.com/tio/app.html#/findings/host-vulnerabilities?f=%s";
    private static final String BASE64_FILTER_PATTERN = "[{\"id\":\"asset.id\",\"isFilterSet\":true,\"operator\":\"eq\",\"value\":[\"%s\"]},{\"id\":\"definition.id\",\"isFilterSet\":true,\"operator\":\"eq\",\"value\":[%d]}]";


    /* groups cves of vulnerabilties by severity for an instance */
    @SneakyThrows
    public List<Map<String, Object>> groupCSVulnerabilities(List<Map<String, Object>> mappedData) {
        Map<String, Object> devices = new HashMap<>();
        Set<String> severities = new HashSet<>(5);

        for (Map<String, Object> mappedLine : mappedData) {
            String deviceId = (String) mappedLine.get("externalId");
            if (StringUtils.isNullOrEmpty(deviceId) || !StringUtils.isNullOrEmpty((String) mappedLine.get("closedDate"))) {
                continue;
            }

            Map<String, Object> instance;
            if (!devices.containsKey(deviceId)) {
                instance = new HashMap<>();
                copyMapFields(mappedLine, instance, "instanceId", "deviceId", "accountid", "region", "externalId", "id");
                instance.put("id", mappedLine.get("externalAccountId") + "-" + deviceId);
                devices.put(deviceId, instance);
            } else {
                instance = (Map<String, Object>) devices.get(deviceId);
            }

            String cveId = (String) mappedLine.get("cveId");
            String severity = (String) mappedLine.get("severity");
            severities.add(severity);

            Map<String, Object> cveByApps;
            if (!instance.containsKey(severity)) {
                cveByApps = new HashMap<>();
                instance.put(severity, cveByApps);
            } else {
                cveByApps = (Map<String, Object>) instance.get(severity);
            }

            List<Map<String, Object>> instanceApps = (List<Map<String, Object>>) mappedLine.get("apps");
            for (Map<String, Object> app : instanceApps) {
                String appName = (String) app.get("product_name_version");
                List<Map<String, String>> cves;
                Map<String, Object> appCve;
                if (!cveByApps.containsKey(appName)) {
                    appCve = new HashMap<>();
                    appCve.put("url", String.format(CROWDSTRIKE_VULNINFO_DEEP_LINK_URL, instance.get("region"), encodeUrlParam(appName), severity.toUpperCase(), encodeUrlParam(String.valueOf(mappedLine.get("hostName")))));
                    appCve.put("title", appName);
                    cveByApps.put(appName, appCve);
                } else {
                    appCve = (Map<String, Object>) cveByApps.get(appName);
                }

                if (!appCve.containsKey("cves")) {
                    cves = new ArrayList<>();
                    appCve.put("cves", cves);
                } else {
                    cves = (List<Map<String, String>>) appCve.get("cves");
                }
                Map<String, String> cve = new HashMap<>();
                cve.put("title", cveId);
                cve.put("url", NIST_VULN_DETAILS_URL + cveId);
                cves.add(cve);
            }
        }
        devices.forEach((k, v) -> {
            Map<String, Object> instance = (Map<String, Object>) v;
            for (String severity : severities) {
                Map<String, Object> cveByApps = (Map<String, Object>) instance.get(severity);
                if (cveByApps != null && !cveByApps.isEmpty()) {
                    instance.put(severity, cveByApps.entrySet().stream().map(Map.Entry::getValue).map(obj -> (Map<String, Object>) obj).collect(Collectors.toList()));
                }
            }

        });
        return devices.entrySet().stream().map(Map.Entry::getValue).map(obj -> (Map<String, Object>) obj).collect(Collectors.toList());
    }

    @SneakyThrows
    public List<Map<String, Object>> mapCSDetections(List<Map<String, Object>> mappedData) {
        Map<String, Object> devices = new HashMap<>();
        Set<String> severities = new HashSet<>(5);

        for (Map<String, Object> mappedLine : mappedData) {
            String deviceId = (String) mappedLine.get("externalId");
            if (StringUtils.isNullOrEmpty(deviceId)) {
                continue;
            }

            Map<String, Object> instance;
            if (!devices.containsKey(deviceId)) {
                instance = new HashMap<>();
                copyMapFields(mappedLine, instance, "instanceId", "accountId", "externalId", "externalAccountId");
                instance.put("id", mappedLine.get("externalAccountId") + "-" + mappedLine.get("externalId"));
                devices.put(deviceId, instance);
            } else {
                instance = (Map<String, Object>) devices.get(deviceId);
            }


            String severity = ((String) mappedLine.get("severity")).toLowerCase();
            severities.add(severity);

            Map<String, Object> tacticTechniqueByFile;
            if (!instance.containsKey(severity)) {
                tacticTechniqueByFile = new HashMap<>();
                instance.put(severity, tacticTechniqueByFile);
            } else {
                tacticTechniqueByFile = (Map<String, Object>) instance.get(severity);
            }

            List<Map<String, String>> cves;
            Map<String, Object> fileDetectionInfo;
            String filePath = (String) mappedLine.get("filepath");
            String deepLinkUrl = (String) mappedLine.get("deepLinkUrl");
            String baseUrl = deepLinkUrl.substring(0, deepLinkUrl.indexOf(ACTIVITY_V2_PATH));
            if (!tacticTechniqueByFile.containsKey(filePath)) {
                fileDetectionInfo = new HashMap<>();
                fileDetectionInfo.put("url", baseUrl + String.format(CROWDSTRIKE_DETECTION_DEEP_LINK_PATH, mappedLine.get("severity"), encodeUrlParam((String) mappedLine.get("filename")), deviceId));
                fileDetectionInfo.put("title", filePath);
                tacticTechniqueByFile.put(filePath, fileDetectionInfo);
            } else {
                fileDetectionInfo = (Map<String, Object>) tacticTechniqueByFile.get(filePath);
            }

            if (!fileDetectionInfo.containsKey("cves")) {
                cves = new ArrayList<>();
                fileDetectionInfo.put("cves", cves);
            } else {
                cves = (List<Map<String, String>>) fileDetectionInfo.get("cves");
            }
            String title = mappedLine.get("tactic") + " via " + mappedLine.get("technique");
            Map<String, String> tacticTechnique = new HashMap<>();
            tacticTechnique.put("title", title);
            tacticTechnique.put("url", getTechniqueUrl((String) mappedLine.get("technique"), (String) mappedLine.get("techniqueId"), baseUrl));
            cves.add(tacticTechnique);
        }
        devices.forEach((k, v) -> {
            Map<String, Object> instance = (Map<String, Object>) v;
            for (String severity : severities) {
                Map<String, Object> tacticTechniqueByFile = (Map<String, Object>) instance.get(severity);
                if (tacticTechniqueByFile != null && !tacticTechniqueByFile.isEmpty()) {
                    instance.put(severity, tacticTechniqueByFile.entrySet().stream().map(Map.Entry::getValue).map(obj -> (Map<String, Object>) obj).collect(Collectors.toList()));
                }
            }

        });
        return devices.entrySet().stream().map(Map.Entry::getValue).map(obj -> (Map<String, Object>) obj).collect(Collectors.toList());
    }

    private String getTechniqueUrl(String techniqueName, String techniqueId, String falconBaseUrl) {

        String url;
        /* for Falcon Detection Method techniqueId starts with CS Eg.CST0010*/
        if (techniqueId.startsWith("CS")) {
            url = falconBaseUrl + "/documentation/detections/technique/" + techniqueName.toLowerCase().replace(" ", "-") + "-" + techniqueId.toLowerCase();
        } else {
            /* Technique can have sub-technique which is in the format techniqueId.subTechniqueId. Eg.T1564.004 */
            String[] techniqueIds = techniqueId.split("\\.");
            url = MITRE_TECHNIQUE_URL + techniqueIds[0] + (techniqueIds.length > 1 ? "/" + techniqueIds[1] : "");
        }
        return url;
    }

    private void copyMapFields(Map<String, Object> src, Map<String, Object> dest, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (src.get(fieldName) != null && !StringUtils.isNullOrEmpty(src.get(fieldName).toString())) {
                dest.put(fieldName, src.get(fieldName));
            }
        }
    }

    private String encodeUrlParam(String text) throws UnsupportedEncodingException {
        return URLEncoder.encode(text, "UTF-8").replace("+", "%20");
    }


    public static List<Map<String, Object>> groupTenableVulnerabilities(List<Map<String, Object>> mappedData) {
        Map<String, Map<String, Object>> groupedByAssetUuid = new HashMap<>();
        Set<String> severities = mappedData.stream()
                .map(entry -> (String) entry.get("severity"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (Map<String, Object> entry : mappedData) {
            String assetUuid = (String) entry.get("asset_uuid");
            String severity = (String) entry.get("severity");
            if (!groupedByAssetUuid.containsKey(assetUuid)) {
                Map<String, Object> groupedEntry = new HashMap<>(entry);
                for (String sev : severities) {
                    groupedEntry.put(sev, new ArrayList<Map<String, Object>>());
                }
                groupedByAssetUuid.put(assetUuid, groupedEntry);
            }
            Map<String, Object> groupedEntry = groupedByAssetUuid.get(assetUuid);
            Map<String, Object> vulnerability = new HashMap<>();
            vulnerability.put("title", entry.get("title"));
            Long violationID = ( (Integer) entry.get("id")).longValue();
            String filter = Base64.getEncoder().encodeToString(String.format(BASE64_FILTER_PATTERN, assetUuid, violationID).getBytes());
            String vulURL = String.format(TENABLE_VULN_DIRECT_URL_TEMPLATE, filter);
            vulnerability.put("url", vulURL);
            List<String> cveList = (List<String>) entry.get("cve");
            List<Map<String, String>> cveMapList = new ArrayList<>();
            if (cveList != null) {
                cveMapList = cveList.stream()
                        .filter(Objects::nonNull)
                        .map(cve -> {
                            Map<String, String> cveMap = new HashMap<>();
                            cveMap.put("title", cve);
                            cveMap.put("url", NIST_VULN_DETAILS_URL + cve);
                            return cveMap;
                        })
                        .collect(Collectors.toList());
            }
            vulnerability.put("cves", cveMapList);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> severityList = (List<Map<String, Object>>) groupedEntry.get(severity);
            if(severityList != null){
                severityList = new ArrayList<>();
            }
            severityList.add(vulnerability);
            groupedEntry.put(severity,severityList);
        }
        return new ArrayList<>(groupedByAssetUuid.values());
    }



    /* generate docid base on cloudtype */
    @SneakyThrows
    public List<Map<String, Object>> generateDocID(List<Map<String, Object>> mappedData) {
        for (Map<String, Object> resourceObject : mappedData) {
            String source = (String) resourceObject.get("source");
            if (source == null) continue;

            StringBuilder docIDBuilder = new StringBuilder();
            switch (source.toLowerCase()) {
                case "aws":
                    appendIfNotNull(docIDBuilder, resourceObject.get("source"));
                    appendIfNotNull(docIDBuilder, resourceObject.get("_entityType"));
                    appendIfNotNull(docIDBuilder, resourceObject.get("account_id"));
                    appendIfNotNull(docIDBuilder, resourceObject.get("region"));
                    appendIfNotNull(docIDBuilder, resourceObject.get("instanceid"));
                    break;

                case "azure":
                    if (resourceObject.get("subscription") != null &&
                            resourceObject.get("resourceGroupName") != null &&
                            resourceObject.get("name") != null) {
                        docIDBuilder.append("subscriptions/")
                                .append(resourceObject.get("subscription")).append("/resourceGroups/")
                                .append(resourceObject.get("resourceGroupName")).append("/providers/Microsoft.Compute/virtualMachines/")
                                .append(resourceObject.get("name"));
                    }
                    break;

                case "gcp":
                    appendIfNotNull(docIDBuilder, resourceObject.get("source"));
                    appendIfNotNull(docIDBuilder, resourceObject.get("_entityType"));
                    appendIfNotNull(docIDBuilder, resourceObject.get("projectId"));
                    appendIfNotNull(docIDBuilder, resourceObject.get("region"));
                    appendIfNotNull(docIDBuilder, resourceObject.get("id"));
                    break;

                default:
                    break;
            }

            String docID = docIDBuilder.toString();
            if (!docID.isEmpty()) {
                resourceObject.put(DOC_ID, docID);
            }
        }
        return mappedData;
    }

    // Helper method to append values if they are not null
    private void appendIfNotNull(StringBuilder builder, Object value) {
        if (value != null) {
            if (builder.length() > 0) {
                builder.append("_"); // Separator for AWS and GCP docIDs
            }
            builder.append(value.toString());
        }
    }


}