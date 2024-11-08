package com.paladincloud.common.aws;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue.Type;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

public class DynamoDBHelper {

    private static final Logger LOGGER = LogManager.getLogger(DynamoDBHelper.class);

    private DynamoDBHelper() {
    }

    static private DynamoDbClient getClient(String region,
        AwsCredentialsProvider credentialsProvider) {
        var builder = DynamoDbClient.builder().region(Region.of(region));
        if (credentialsProvider != null) {
            builder.credentialsProvider(credentialsProvider);
        }
        return builder.build();
    }

    public static Map<String, String> get(String region, AwsCredentialsProvider credentialsProvider,
        String tableName,
        String key, String keyValue, Map<String, String> fieldMap) {
        var keyMap = new HashMap<>(Map.of(key, AttributeValue.builder().s(keyValue).build()));

        var request = GetItemRequest.builder().key(keyMap).tableName(tableName).build();
        try (var client = getClient(region, credentialsProvider)) {
            LOGGER.info(
                String.format("Querying '%s' for item: %s (%s)", tableName, request, client));
            var item = client.getItem(request).item();
            return getFieldsFromRow(item, fieldMap);
        }
    }

    public static List<Map<String, String>> query(String region,
        AwsCredentialsProvider credentialsProvider, String tableName,
        String key, String keyValue, Map<String, String> fieldMap) {
        var attributeNameAlias = Map.of("#" + key, key);
        var attributeValues = Map.of(":" + key, AttributeValue.builder().s(keyValue).build());
        var request = QueryRequest.builder().tableName(tableName)
            .keyConditionExpression(String.format("#%s = :%s", key, key))
            .expressionAttributeNames(attributeNameAlias)
            .expressionAttributeValues(attributeValues)
            .build();

        try (var client = getClient(region, credentialsProvider)) {
            var response = client.query(request);
            if (response.hasItems()) {
                var convertedRows = new ArrayList<Map<String, String>>();
                for (var row : response.items()) {
                    convertedRows.add(getFieldsFromRow(row, fieldMap));
                }
                return convertedRows;
            }
        }

        return Collections.emptyList();
    }

    private static Map<String, String> getFieldsFromRow(Map<String, AttributeValue> row,
        Map<String, String> fieldMap) {
        var configResponse = new HashMap<String, String>();
        fieldMap.forEach((fullFieldName, fieldValue) -> {
            var fieldName = fullFieldName;
            ;
            var dottedName = "";
            if (fieldName.contains(".")) {
                fieldName = fullFieldName.substring(0, fullFieldName.lastIndexOf('.'));
                dottedName = fullFieldName.substring(fullFieldName.lastIndexOf('.') + 1);
            }

            var rowValue = row.get(fieldName);
            if (rowValue != null) {
                if (rowValue.hasM()) {
                    configResponse.put(fieldValue, rowValue.m().get(dottedName).s());
                } else if (rowValue.type().equals(Type.S)) {
                    configResponse.put(fieldValue, rowValue.s());
                } else {
                    LOGGER.error(
                        "Unable to convert value for '{}: '{}'", fieldName, rowValue);
                }
            }
        });
        return configResponse;
    }
}
