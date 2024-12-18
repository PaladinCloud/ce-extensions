package com.paladincloud.common.aws;

import java.util.ArrayList;
import java.util.Arrays;
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

    static private DynamoDbClient getClient(String region, AwsCredentialsProvider credentialsProvider) {
        var builder = DynamoDbClient.builder().region(Region.of(region));
        if (credentialsProvider != null) {
            builder.credentialsProvider(credentialsProvider);
        }
        return builder.build();
    }

    public static Map<String, String> get(String region, AwsCredentialsProvider credentialsProvider, String tableName,
        String key, String keyValue, Map<String, String> fieldMap) {
        var keyMap = new HashMap<>(Map.of(key, AttributeValue.builder().s(keyValue).build()));

        var request = GetItemRequest.builder().key(keyMap).tableName(tableName).build();
        try (var client = getClient(region, credentialsProvider)) {
            LOGGER.info(STR."Querying '\{tableName}' for item: \{request}");
            var item = client.getItem(request).item();
            return getFieldsFromRow(item, fieldMap);
        }
    }

    public static List<Map<String, String>> query(String region, AwsCredentialsProvider credentialsProvider, String tableName,
        String key, String keyValue, Map<String, String> fieldMap) {
        var attributeNameAlias = Map.of(STR."#\{key}", key);
        var attributeValues = Map.of(STR.":\{key}", AttributeValue.builder().s(keyValue).build());
        var request = QueryRequest.builder().tableName(tableName)
            .keyConditionExpression(STR."#\{key} = :\{key}")
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
            var fieldByLevel = fullFieldName.split("\\.");
            if (fieldByLevel.length > 1) {
                fieldName = fieldByLevel[0];
            }

            var rowValue = row.get(fieldName);
            if (rowValue != null) {
                if (rowValue.hasM()) {
                    var val = getUnderlyingValue(rowValue.m(), Arrays.stream(fieldByLevel).skip(1).toList());
                    if (val != null) {
                        configResponse.put(fieldValue, val.toString());
                    }
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

    private static Object getUnderlyingValue(Map<String, AttributeValue> val, List<String> levels) {
        Map<String, AttributeValue> curMap = val;
        AttributeValue curVal = null;
        for (var level : levels) {
            curVal = curMap.get(level);
            if (curVal == null) {
                return null;
            }
            if (curVal.hasM()) {
                curMap = curVal.m();
            }
        }

        if (curVal != null) {
            switch (curVal.type()) {
                case S:
                    return curVal.s();
                case BOOL:
                    return curVal.bool();
            }
        }
        return null;
    }
}
