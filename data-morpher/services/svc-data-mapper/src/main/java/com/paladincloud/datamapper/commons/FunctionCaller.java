/*******************************************************************************
 * Copyright 2023 Paladin Cloud, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package com.paladincloud.datamapper.commons;

import com.amazonaws.util.ImmutableMapParameter;
import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.paladincloud.datamapper.utils.rules.RulesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.paladincloud.datamapper.commons.FunctionCallerConstants.AUTO_REPAIR_KEY;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.AUTO_REPAIR_NODE_KEY;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.AUTO_UPGRADE_KEY;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.AUTO_UPGRADE_NODE_KEY;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.BOOLEAN_FALSE_AS_STRING;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.BOOLEAN_TRUE_AS_STRING;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.CONFIG_KEY;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.DATE_FORMAT;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.ENABLE_INTEGRITY_KEY;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.ENABLE_INTEGRITY_NODE_KEY;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.ENABLE_SECURE_BOOT_KEY;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.ENABLE_SECURE_BOOT_NODE_KEY;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.IAM_RESOURCE_FORMAT;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.JSON_FIELD_SEPARATOR;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.JSON_KEY_VALUE_SEPARATOR;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.KEY_FIELD;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.MANAGEMENT_KEY;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.NAME_KEY;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.NODE_NAMES;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.SHIELDED_INSTANCE_CONFIG_KEY;
import static com.paladincloud.datamapper.commons.FunctionCallerConstants.VALUE_FIELD;
import static com.paladincloud.datamapper.commons.MapperConstants.FIELD_STARTS_WITH_HASH;
import static com.paladincloud.datamapper.commons.MapperConstants.FUNCTION_ARGUMENTS_DELIMITER;
import static com.paladincloud.datamapper.commons.MapperConstants.JSON_ROOT_PATH;
import static com.paladincloud.datamapper.commons.MapperConstants.MULTI_SOURCE_FIELD_DELIMITER;

public class FunctionCaller {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(FunctionCaller.class);
    private static final List<String> cloudTypes = Arrays.asList("aws", "azure", "gcp");
    private static final Map<String, String> cloudVMs = ImmutableMapParameter.of("aws", "ec2", "azure", "virtualmachine", "gcp", "vminstance");

    public static Object callFunction(String functionCall, DocumentContext resourceJson,
                                      Map<String, Object> multiSourceObject) {
        try {
            functionCall = functionCall.replaceFirst("@", "");
            int openParenIndex = functionCall.indexOf("(");
            int closeParenIndex = functionCall.lastIndexOf(")");
            String functionName = functionCall.substring(0, openParenIndex);
            String parameterString = functionCall.substring(openParenIndex + 1, closeParenIndex);
            Class<?> clazz = FunctionCaller.class;

            if (parameterString.length() == 0) {
                Method method = clazz.getDeclaredMethod(functionName);
                Object instance = clazz.getDeclaredConstructor().newInstance();
                return method.invoke(instance);
            }

            String[] parameterTokens = parameterString.split(FUNCTION_ARGUMENTS_DELIMITER);
            replaceFieldValue(parameterTokens, resourceJson, multiSourceObject);
            Method method = clazz.getDeclaredMethod(functionName, getParameterTypes(parameterTokens));
            Object instance = clazz.getDeclaredConstructor().newInstance();
            return method.invoke(instance, getParameters(parameterTokens));
        } catch (PathNotFoundException e) {
            return null;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    private static Class<?>[] getParameterTypes(String[] parameterTokens) {
        List<Class<?>> parameterTypes = new ArrayList<>();
        for (String token : parameterTokens) {
            if (token.matches("\\d+") && token.length() < 9) {
                parameterTypes.add(int.class);
            } else if (token.matches("\\d+") && token.length() >= 9) {
                parameterTypes.add(long.class);
            } else if (token.equalsIgnoreCase(BOOLEAN_TRUE_AS_STRING) || token.equalsIgnoreCase(BOOLEAN_FALSE_AS_STRING)) {
                parameterTypes.add(boolean.class);
            } else {
                parameterTypes.add(String.class);
            }
        }

        return parameterTypes.toArray(new Class<?>[0]);
    }

    private static Object[] getParameters(String[] parameterTokens) {
        Object[] parameters = new Object[parameterTokens.length];
        for (int i = 0; i < parameterTokens.length; i++) {
            if (parameterTokens[i].matches("\\d+") && parameterTokens[i].length() < 9) {
                parameters[i] = Integer.parseInt(parameterTokens[i]);
            } else if (parameterTokens[i].matches("\\d+") && parameterTokens[i].length() >= 9) {
                parameters[i] = Long.parseLong(parameterTokens[i]);
            } else if (parameterTokens[i].equalsIgnoreCase(BOOLEAN_TRUE_AS_STRING)) {
                parameters[i] = true;
            } else if (parameterTokens[i].equalsIgnoreCase(BOOLEAN_FALSE_AS_STRING)) {
                parameters[i] = false;
            } else {
                parameters[i] = parameterTokens[i];
            }
        }

        return parameters;
    }

    private static void replaceFieldValue(String[] parameterTokens, DocumentContext resourceJson,
                                          Map<String, Object> multiSourceObject) throws JsonProcessingException {

        for (int i = 0; i < parameterTokens.length; i++) {
            ObjectMapper mapper = new ObjectMapper();
            if (parameterTokens[i].startsWith("$")) {
                if (resourceJson.read(parameterTokens[i]) instanceof LinkedHashMap) {
                    parameterTokens[i] = mapper.writeValueAsString(resourceJson.read(parameterTokens[i]));
                } else {
                    Object fieldValue = resourceJson.read(parameterTokens[i]);
                    parameterTokens[i] = fieldValue != null ? fieldValue.toString() : "";
                }
            } else if (parameterTokens[i].startsWith(FIELD_STARTS_WITH_HASH)) {
                Object fieldValue = getValueFromMultiSourceObject(parameterTokens[i], resourceJson, multiSourceObject);
                parameterTokens[i] = fieldValue != null ? fieldValue.toString() : "";
            } else if (parameterTokens[i].startsWith("@")) {
                parameterTokens[i] = callFunction(parameterTokens[i], resourceJson, multiSourceObject).toString();
            }
        }
    }

    public static String findValueByReverseIndex(String input, String delimiter, int reverseIndex) {
        String[] parts = input.split(delimiter);

        if (reverseIndex >= 0 && reverseIndex < parts.length) {
            return parts[parts.length - 1 - reverseIndex];
        }

        return null;
    }

    public static String findValueAtIndex(String input, String delimiter, int index) {
        String[] parts = input.split(delimiter);

        if (index >= 0 && index < parts.length) {
            return parts[index];
        }

        return null;
    }

    public static Object convertJsonArrayToObject(String jsonArray) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonArray);

            StringBuilder jsonObjectBuilder = new StringBuilder();
            jsonObjectBuilder.append("{");

            for (JsonNode entry : jsonNode) {
                String key = entry.get(KEY_FIELD).asText();
                String value = entry.get(VALUE_FIELD).asText();

                jsonObjectBuilder.append("\"").append(key)
                        .append("\"" + JSON_KEY_VALUE_SEPARATOR + "\"")
                        .append(value).append("\"" + JSON_FIELD_SEPARATOR);
            }

            jsonObjectBuilder.deleteCharAt(jsonObjectBuilder.length() - 1); // for comma
            jsonObjectBuilder.append("}");
            return objectMapper.readValue(jsonObjectBuilder.toString(), Object.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static int countJsonNodes(String jsonString) {
        try {
            int count = 0;
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            for (JsonNode ignored : jsonNode) {
                count += 1;
            }

            return count;
        } catch (Exception e) {
            e.printStackTrace();
            return -1; // for error
        }
    }

    public static long convertMillisecondsToSeconds(int durationMillis) {
        return TimeUnit.NANOSECONDS.toSeconds(durationMillis);
    }

    public static long convertMillisecondsToSeconds(long durationMillis) {
        return TimeUnit.NANOSECONDS.toSeconds(durationMillis);
    }

    public static Object createRestrictionsForApiKeys(String inputJson) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode inputNode;
        try {
            inputNode = objectMapper.readTree(inputJson);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing input JSON", e);
        }

        ObjectNode outputNode = objectMapper.createObjectNode();

        for (String nodeName : NODE_NAMES) {
            if (inputNode.has(nodeName)) {
                outputNode.set(makeFirstLetterSmall(nodeName), inputNode.get(nodeName));
            } else {
                outputNode.set(makeFirstLetterSmall(nodeName), objectMapper.createObjectNode());
            }
        }

        return objectMapper.convertValue(outputNode, Object.class);
    }

    public static String makeFirstLetterSmall(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder stringBuilder = new StringBuilder(input);
        stringBuilder.setCharAt(0, Character.toLowerCase(stringBuilder.charAt(0)));
        return stringBuilder.toString();
    }

    public static String joinStrings(String delimiter, String field1, String field2) {
        if (field1 == null || field2 == null) {
            return "";
        }

        return field1 + delimiter + field2;
    }

    public static String getIAMResourceName(String project, String dataset, String table) {
        return String.format(IAM_RESOURCE_FORMAT, project, dataset, table);
    }

    public String getAccountId(String accountId, String substring) {
        if (accountId != null) {
            accountId = accountId.substring(substring.length());
        }

        return accountId;
    }

    public String getLastIndexOf(String string, String delimiter) {
        return string.substring(string.lastIndexOf(delimiter) + 1);
    }

    public String getAsString(long value) {
        return Long.toString(value);
    }

    public String getDiscoveryDate() {
        return new SimpleDateFormat(MapperConstants.DEFAULT_OPEN_SEARCH_DATE_FORMAT).format(new java.util.Date());
    }

    public String convertSeverity(String severityInput) {
        String severity = null;
        if (severityInput != null) {
            try {
                Severity severityObj = Severity.valueOf(severityInput.toUpperCase());
                severity = severityObj.getValue();
            } catch (IllegalArgumentException e) {
                logger.error("Invalid severity level: {}", severityInput, e);
            }
        }

        return severity;
    }

    public String getVulnerabilityDescription(String title, String path) {
        String occurrence = "occurred in ";
        if (title != null && path != null) {
            return title + " " + occurrence + path;
        }
        return title != null ? title : path;
    }

    public String convertSeverityForCheckmarx(String severityInput) {
        if (severityInput == null) {
            return null;
        }
        try {
            Severity severityObj = Severity.valueOf("CHECKMARX_" + severityInput.toUpperCase());
            return severityObj.getValue();
        } catch (IllegalArgumentException e) {
            logger.error("Invalid severity level: {}", severityInput);
            return null;
        }
    }

    public String convertSeverityForPlugin(String plugin, String severity) {
        if (severity == null) {
            return null;
        }
        try {
            Severity severityObj = Severity.valueOf((plugin + "_" + severity).toUpperCase());
            return severityObj.getValue();
        } catch (IllegalArgumentException e) {
            logger.error("Invalid severity level: {}", severity);
            return null;
        }
    }

    public String getResolutionUrl(int alertId) {
        return getResolutionUrl(Integer.toString(alertId));
    }

    public String getResolutionUrl(String alertId) {
        if (alertId == null) {
            return null;
        }
        String zaProxyUrl = "https://www.zaproxy.org/docs/alerts/";
        return zaProxyUrl + alertId;
    }

    public String convertStatus(String statusInput) {
        String status = null;
        if (statusInput != null) {
            try {
                Status statusObj = Status.valueOf(statusInput.toUpperCase());
                status = statusObj.getValue();
            } catch (IllegalArgumentException e) {
                logger.error("Invalid status: {}", statusInput, e);
            }
        }

        return status;
    }

    public String convertStatusForCheckmarx(String statusInput) {
        if (statusInput == null) {
            return null;
        }
        try {
            Status statusObj = Status.valueOf(statusInput.toUpperCase());
            return statusObj.getValue();
        } catch (IllegalArgumentException e) {
            logger.error("Invalid status: {}", statusInput);
            return null;
        }
    }

    public String convertPolicyStatus(boolean isDisabled) {
        if (isDisabled) {
            return "DISABLED";
        }

        return "ENABLED";
    }

    public String getFormattedDate(int timeData) {
        if (timeData != 0) {
            return getFormattedDate((long) timeData);
        }
        return null;
    }

    public String getFormattedDate(long timeData) {
        if (timeData != 0L) {
            try {
                return new SimpleDateFormat(DATE_FORMAT).format(new Date(timeData));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public static Object getConvertedNodePools(String nodePools) {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> transformedItems = new ArrayList<>();

        try {
            JsonNode originalNode = mapper.readTree(nodePools);
            for (JsonNode itemNode : originalNode) {
                Map<String, Object> transformedItem = new HashMap<>();
                transformedItem.put(NAME_KEY, itemNode.get(NAME_KEY).asText());

                JsonNode managementNode = itemNode.get(MANAGEMENT_KEY);
                if (managementNode != null) {
                    transformedItem.put(AUTO_UPGRADE_NODE_KEY, managementNode.get(AUTO_UPGRADE_KEY).asBoolean());
                    transformedItem.put(AUTO_REPAIR_NODE_KEY, managementNode.get(AUTO_REPAIR_KEY).asBoolean());
                }

                JsonNode configNode = itemNode.get(CONFIG_KEY);
                if (configNode != null) {
                    JsonNode shieldedConfigNode = configNode.get(SHIELDED_INSTANCE_CONFIG_KEY);
                    if (shieldedConfigNode != null) {
                        transformedItem.put(ENABLE_INTEGRITY_NODE_KEY,
                                shieldedConfigNode.get(ENABLE_INTEGRITY_KEY).asBoolean());
                        transformedItem.put(ENABLE_SECURE_BOOT_NODE_KEY,
                                shieldedConfigNode.get(ENABLE_SECURE_BOOT_KEY).asBoolean());
                    }
                }

                transformedItems.add(transformedItem);
            }
            return mapper.convertValue(transformedItems, Object.class);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static String generateRedHatURL(String urlPrefix, String accountId, String subString, String urlSuffix,
                                           String violationId) {
        if (accountId != null && subString != null) {
            accountId = accountId.substring(subString.length());
        }

        return urlPrefix + accountId + urlSuffix + violationId;
    }

    /**
     * This method is used to get the region from function name for asset Type
     * gcp-function
     *
     * @return region
     */
    public static String getRegionFromName(String functionName) {
        if (null != functionName && !functionName.isEmpty()) {
            String[] attributesArray = functionName.split("/");
            return attributesArray[3];
        }

        return null;
    }

    public static String toLowerCase(String input) {
        if (input == null) {
            return null;
        }

        return input.toLowerCase();
    }

    public static String concatenateStrings(String string1, String string2) {
        return string1 + string2;
    }

    public static String concatenateStrings(String string1, String string2, String string3, String string4, String string5) {
        return string1 + string2 + string3 + string4 + string5;
    }

    public static String concatenateStrings(String string1, String string2, String string3) {
        return string1 + string2 + string3;
    }

    public static String generateContrastURL(String input, String arg1, String arg2) {
        return String.format(input, arg1, arg2);
    }

    public static String convertDateTimeFormat(String inputDateTimeString, String currentDateFormat, String expectedDateFormat) {
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern(currentDateFormat);
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(expectedDateFormat);

        // Parse input string to LocalDateTime
        LocalDateTime localDateTime = LocalDateTime.parse(inputDateTimeString, inputFormatter);

        // Format LocalDateTime to desired format
        return outputFormatter.format(localDateTime.atOffset(java.time.ZoneOffset.UTC));
    }

    public static String isPolicyEnabledInContrast(boolean enabled_dev, boolean enabled_prod, boolean enabled_qa) {
        if (enabled_dev || enabled_prod || enabled_qa) {
            return MapperConstants.STATUS_ENABLED;
        }

        return MapperConstants.STATUS_DISABLED;
    }

    public static Object getValueFromMultiSourceObject(String field, DocumentContext resourceJson,
                                                       Map<String, Object> multiSourceObject) {
        if (field == null || field.length() < 2) {
            return null;
        }

        try {
            String replacerField = field.substring(FIELD_STARTS_WITH_HASH.length(), field.length() - 1);
            /* contains two fields [field_jsonpath, replacer_filed_key] */
            String[] fields = replacerField.split(MULTI_SOURCE_FIELD_DELIMITER);
            if (fields.length < 2) {
                return null;
            }

            ObjectMapper mapper = new ObjectMapper();
            String objPK = mapper.writeValueAsString(resourceJson.read(fields[1].trim()));
            String objectKey = String.format(fields[0], objPK);
            return JsonPath.read(multiSourceObject, JSON_ROOT_PATH + objectKey);
        } catch (Exception e) {
            logger.error("Error in getting value from source object {}", field, e);
        }

        return null;
    }

    public String convertToOpenSearchDateFormat(int timeData) {
        if (timeData != 0) {
            return convertToOpenSearchDateFormat((long) timeData);
        }

        return null;
    }

    public String convertToOpenSearchDateFormat(long timeData) {
        if (timeData != 0L) {
            try {
                return new SimpleDateFormat(DATE_FORMAT).format(new Date(timeData));
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }

    public String convertToOpenSearchDateFormat(String dateString) {
        return convertDateTimeFormat(dateString, MapperConstants.CQ_OUTPUT_DATE_FORMAT, MapperConstants.DEFAULT_OPEN_SEARCH_DATE_FORMAT);
    }

    public String convertScanTimeToElasticFormat(String inputDateTimeString) {
        if (inputDateTimeString == null) {
            return null;
        }
        // Parse the input date string into a ZonedDateTime object in UTC timezone
        ZonedDateTime zonedDateTime = Instant.parse(inputDateTimeString).atZone(ZoneOffset.UTC);
        // Format the ZonedDateTime object into the desired format for Elasticsearch
        return zonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
    }

    public static String checkWithPriority(String param1, String param2, String param3) {
        if (param1 == null || param1.isEmpty()) {
            if (param2 == null || param2.isEmpty()) {
                return param3;
            } else {
                return param2;
            }
        } else {
            return param1;
        }
    }

    public static String getCloudTypeFromNameOrDefault(String name, String defaultName) {
        if (StringUtils.isNullOrEmpty(name)) {
            return defaultName;
        }
        return cloudTypes.stream()
                .filter(cloudType -> toLowerCase(name).contains(cloudType))
                .findFirst()
                .orElse(name);
    }

    public static String getRapid7AssetSource(String inputJson) {
        JsonNode inputNode;
        try {
            inputNode = new ObjectMapper().readTree(inputJson);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing input JSON", e);
        }

        for (JsonNode entry : inputNode) {
            String source = entry.get("source").asText();
            switch (source) {
                case "amazon-web-services":
                    return "aws";
                case "azure":
                    return "azure";
                case "google-cloud-platform":
                    return "gcp";
                default:
                    break;
            }
        }

        return null;
    }

    public static String getRapid7AssetId(String inputJson) {
        JsonNode inputNode;
        try {
            inputNode = new ObjectMapper().readTree(inputJson);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing input JSON", e);
        }

        for (JsonNode entry : inputNode) {
            String source = entry.get("source").asText();
            switch (source) {
                case "amazon-web-services":
                    return entry.get("id").asText();
                case "azure_vmid":
                    return entry.get("id").asText();
                case "google-cloud-platform":
                    return entry.get("id").asText();
                default:
                    break;
            }
        }

        return null;
    }

    public static Object getRapid7Cves(String inputJson, String severity) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode inputNode;
        try {
            inputNode = objectMapper.readTree(inputJson);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing input JSON", e);
        }

        List<Object> cves = new ArrayList<>();
        for (JsonNode entry : inputNode) {
            String id = entry.get("vulnerability_id").asText();
            Map<String, Object> fullCve = (Map<String, Object>) RulesService.getInstance().getRule(id);
            if (fullCve == null) {
                logger.warn("Unable to find matching Rapid7 CVE for '{}'", id);
            } else if (fullCve.containsKey("severity") && severity.equalsIgnoreCase((String) fullCve.get("severity"))) {
                fullCve.put("url", "https://www.rapid7.com/db/vulnerabilities/" + id + "/");
                fullCve.put("id", fullCve.get("cves"));
                cves.add(fullCve);
            }
        }

        return objectMapper.convertValue(cves, Object.class);
    }

    public static String generateDocId(String cloud, String targetType, long accountId,
                                       String region, long resourceId) {
        return (cloud + "_" + targetType + "_" + accountId + "_" + region + "_" + resourceId).toLowerCase();
    }

    public static String generateDocId(String cloud, String targetType, long accountId,
                                       String region, String resourceId) {
        return (cloud + "_" + targetType + "_" + accountId + "_" + region + "_" + resourceId).toLowerCase();
    }

    public static String generateDocId(String cloud, String targetType, String accountId,
                                       String region, String resourceId) {
        return (cloud + "_" + targetType + "_" + accountId + "_" + region + "_" + resourceId).toLowerCase();
    }

    public static String generateDocId(String cloud, String targetType, String accountId,
                                       String region, long resourceId) {
        return (cloud + "_" + targetType + "_" + accountId + "_" + region + "_" + resourceId).toLowerCase();
    }

    public static List<Object> emptyArray() {
        return new ArrayList<>();
    }

    public static String getTargetType(String cloudType, String entityType) {
        if ("server".equalsIgnoreCase(entityType)) {
            return cloudVMs.getOrDefault(cloudType, entityType);
        }
        return entityType;
    }

    public static String buildVulnerabilityUrl(String url, String region) {
        return String.format(url, region);
    }

    private static List<ObjectNode> parseHtmlToCveList(String htmlContent) throws Exception {
        List<ObjectNode> cveList = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new java.io.ByteArrayInputStream(htmlContent.getBytes()));
        NodeList liNodes = doc.getElementsByTagName("li");
        for (int i = 0; i < liNodes.getLength(); i++) {
            Node liNode = liNodes.item(i);
            NodeList childNodes = liNode.getChildNodes();
            for (int j = 0; j < childNodes.getLength(); j++) {
                Node childNode = childNodes.item(j);
                if (childNode.getNodeName().equals("a")) {
                    String url = childNode.getAttributes().getNamedItem("href").getNodeValue();
                    String text = childNode.getTextContent();
                    String id = extractCveId(text);
                    if (id != null) {
                        ObjectNode cveObject = mapper.createObjectNode();
                        cveObject.put("id", id);
                        cveObject.put("url", url);
                        cveList.add(cveObject);
                    }
                }
            }
        }

        return cveList;
    }

    private static String extractCveId(String text) {
        if (text.contains(":")) {
            int start = 0;
            int end = text.indexOf(":", start);
            if (end == -1) {
                end = text.length();
            }
            return text.substring(start, end);
        }
        return null;
    }

    public static String formatIssueDetail(String baseUrl, String path, String issueDetail) {
        return "Path: " + baseUrl + path + " | Issue detail: " + issueDetail.replaceAll("<[^>]*>", "");
    }

    public static List<Map<String, String>> createLinks(String cweLink, String owaspLink) {
        List<Map<String, String>> linksArray = new ArrayList<>();
        Map<String, String> cwe = new HashMap<>();
        cwe.put("id", "CWE" + extractIdFromUrl(cweLink));
        cwe.put("url", cweLink);
        Map<String, String> owasp = new HashMap<>();
        owasp.put("id", "OWASP");
        owasp.put("url", owaspLink);
        linksArray.add(cwe);
        linksArray.add(owasp);
        return linksArray;
    }

    private static String extractIdFromUrl(String url) {
        if (url.contains("cwe.mitre.org")) {
            return "-" + url.replaceAll(".*/definitions/(\\d+).html", "$1");
        }
        return "";
    }

    public static List<Map<String, String>> createCheckmarxCWELink(int cweId) {
        List<Map<String, String>> linksArray = new ArrayList<>();
        Map<String, String> cwe = new HashMap<>();
        cwe.put("id", "CWE-" + cweId);
        cwe.put("url", String.format("https://cwe.mitre.org/data/definitions/%s.html", cweId));
        linksArray.add(cwe);
        return linksArray;
    }

    public static List<Map<String, String>> createCheckmarxCWELink(String cweId) {
        return new ArrayList<>();
    }

    public static String jsonToString(String obj){
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(obj);
            if (rootNode instanceof ObjectNode) {
                ObjectNode objectNode = (ObjectNode) rootNode;
                objectNode.remove("_cq_id");
                objectNode.remove("_cq_parent_id");
                objectNode.remove("_cq_source_name");
                objectNode.remove("_cq_sync_time");
                return mapper.writeValueAsString(objectNode);
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static Object getValueFromSpecificIndex(String jsonString, int index){
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            List<Object> arrayList = objectMapper.readValue(jsonString, List.class);
            if(arrayList != null && !arrayList.isEmpty() && arrayList.size()>= index){
                return arrayList.get(index);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error parsing input JSON", e);
        }

        return null;
    }


    public static Object getQualysCvesBySeverity(String jsonString, int severity) {
        ObjectMapper objectMapper = new ObjectMapper();
        List<Map<String,Object>> vulnList;

        try {
             vulnList = objectMapper.readValue(jsonString, List.class);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing input JSON", e);
        }

        List<Object> cves = new ArrayList<>();
        for (Map<String,Object> vuln : vulnList) {
            Map<String,Object> vulnDetails = (Map<String, Object>) vuln.get("HostAssetVuln");
            String qid = String.valueOf(vulnDetails.get("qid"));
            Map<String, Object> qidObject = (Map<String, Object>) RulesService.getInstance().getRule(qid);
            if (qidObject == null) {
                logger.warn("Unable to find matching Qualys CVE for '{}'", qidObject);
            } else if (qidObject.containsKey("severity") && String.valueOf(severity).equalsIgnoreCase((String) qidObject.get("severity"))) {
                Map<String, Object> vulObject = new HashMap<>();
                vulObject.put("title",qidObject.get("title"));
                vulObject.put("url",vulnDetails.get("vulURL"));
                vulObject.put("cves",qidObject.get("cves"));
                cves.add(vulObject);
            }
        }

        return objectMapper.convertValue(cves, Object.class);
    }

    public static String getCloudProvider(String jsonString) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // Parse JSON string to a JsonNode
            JsonNode jsonNode = objectMapper.readTree(jsonString);

            // Iterate over each element of the array
            if (jsonNode.isArray()) {
                for (JsonNode asset : jsonNode) {
                    // Check for "Ec2AssetSourceSimple"
                    if (asset.has("Ec2AssetSourceSimple")) {
                        return "aws";
                    }
                    // Check for "AzureAssetSourceSimple"
                    if (asset.has("AzureAssetSourceSimple")) {
                        return "azure";
                    }
                    // Check for "GcpAssetSourceSimple"
                    if (asset.has("GcpAssetSourceSimple")) {
                        return "gcp";
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Error parsing JSON: " + e.getMessage());
        }

        return "unknown";  // Return "unknown" if none of the conditions match
    }

    public boolean isStateActive(String state){
        if (state == null) {
            return false;
        }
        switch (state.toLowerCase()) {
            case "running":
            case "run":
            case "active":
                return true;
            case "stopped":
            case "terminated":
                return false;
            default:
                return false; // For any other unrecognized states
        }
    }

    public boolean getAssetStateFromHostAsset(String jsonString, int index){
        Object state=  getValueFromSpecificIndex( jsonString,  index);
        return isStateActive((String) state);
    }

    public static String extractRegionFromRegistry(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String[] parts = input.split("-");
        if (parts.length < 2) {
            return "";
        }
        return parts[0] + "-" + parts[1];
    }

    public static String extractArtifactName(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String[] parts = input.split("/");
        if (parts.length < 3) {
            return input;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 2; i < parts.length; i++) {
            if (i > 2) {
                result.append("/");
            }
            result.append(parts[i]);
        }
        return result.toString();
    }

    public static String extractArtifactNameFromID(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        try {
            // Extract the portion after 'dockerImages/'
            String artifactPart = input.substring(input.indexOf("dockerImages/") + "dockerImages/".length());
            // Extract the portion before '@' to get the artifact name
            String artifactNameEncoded = artifactPart.split("@")[0];
            // Decode the artifact name
            return URLDecoder.decode(artifactNameEncoded, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Error decoding artifact name", e);
        } catch (IndexOutOfBoundsException e) {
            return "";
        }
    }

    public static String extractDigest(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // Extract the portion after '@'
        int atIndex = input.indexOf('@');
        if (atIndex != -1 && atIndex < input.length() - 1) {
            return input.substring(atIndex + 1);
        }

        return "";
    }

    public static Object getQualysDockerImageCvesBySeverity(String jsonString, int severity) {
        ObjectMapper objectMapper = new ObjectMapper();
        List<Map<String,Object>> vulnList;

        try {
            vulnList = objectMapper.readValue(jsonString, List.class);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing input JSON", e);
        }

        List<Object> cves = new ArrayList<>();
        for (Map<String,Object> vuln : vulnList) {
            String qid = String.valueOf(vuln.get("qid"));
            Map<String, Object> qidObject = (Map<String, Object>) RulesService.getInstance().getRule(qid);
            if (qidObject == null) {
                logger.warn("Unable to find matching Qualys CVE for '{}'", qidObject);
            } else if (qidObject.containsKey("severity") && String.valueOf(severity).equalsIgnoreCase((String) qidObject.get("severity"))) {
                Map<String, Object> vulObject = new HashMap<>();
                vulObject.put("title",qidObject.get("title"));
                vulObject.put("url",vuln.get("vulURL"));
                vulObject.put("cves",qidObject.get("cves"));
                cves.add(vulObject);
            }
        }

        return objectMapper.convertValue(cves, Object.class);
    }

    public static String removeField(String jsonString, String fieldName) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(jsonString);

        if (rootNode.has(fieldName)) {
            ((ObjectNode) rootNode).remove(fieldName);
        }

        return objectMapper.writeValueAsString(rootNode);
    }

    public static String convertDateToStandardFormat(long timestamp) {
        try {
            // Create a Date object from the timestamp
            Date date = new Date(timestamp);

            // Format the Date object to ISO 8601 format
            SimpleDateFormat iso8601Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            iso8601Format.setTimeZone(TimeZone.getTimeZone("UTC")); // Ensure the output is in UTC
            return iso8601Format.format(date);
        } catch (NumberFormatException e) {
            // Handle errors for invalid input
            return "Error: Invalid input format. Please provide a valid timestamp.";
        }
    }

}
