package com.paladincloud.common.config;

import com.paladincloud.common.aws.DynamoDBHelper;
import com.paladincloud.common.aws.RoleHelper;
import com.paladincloud.common.errors.JobException;
import com.paladincloud.common.util.JsonHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

public class Configuration {

    private Configuration() {
    }

    private static final Properties properties = new Properties();

    public static String get(String propertyName) {
        return get(propertyName, null);
    }

    public static String get(String propertyName, String defaultValue) {
        return properties.getProperty(propertyName, defaultValue);
    }

    public static void retrieveConfiguration(ConfigParams configParams) {
        if (!properties.isEmpty()) {
            return;
        }

        var dynamoConfig = getTenantConfiguration(configParams);
        Configuration.setProperties(dynamoConfig);

        // Database access
        var secretsMap = getSecretsWithRole(configParams);
        properties.put(ConfigConstants.DB_HOST, secretsMap.get(ConfigConstants.DB_HOST));
        properties.put(ConfigConstants.DB_PORT, secretsMap.get(ConfigConstants.DB_PORT));
        properties.put(ConfigConstants.DB_NAME, secretsMap.get(ConfigConstants.DB_NAME));
        properties.put(ConfigConstants.DB_USERNAME, secretsMap.get(ConfigConstants.DB_USERNAME));
        properties.put(ConfigConstants.DB_PASSWORD, secretsMap.get(ConfigConstants.DB_PASSWORD));
    }

    private static Map<String, String> getSecretsWithRole(ConfigParams configParams) {
        return RoleHelper.runAs(configParams.awsRegion, null, configParams.assumeRoleArn,
            secretCredentialsProvider -> {
                var builder = SecretsManagerClient.builder()
                    .region(Region.of(configParams.awsRegion));
                if (secretCredentialsProvider != null) {
                    builder.credentialsProvider(secretCredentialsProvider);
                }
                try (var client = builder.build()) {
                    var request = GetSecretValueRequest.builder()
                        .secretId(configParams.secretNamePrefix + configParams.tenantId)
                        .build();
                    var response = client.getSecretValue(request);
                    try {
                        var strToObjectMap = JsonHelper.mapFromString(response.secretString());
                        return strToObjectMap.entrySet().stream()
                            .collect(
                                Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
                    } catch (Exception e) {
                        throw new JobException("Failed parsing JSON secret", e);
                    }
                }
            });
    }

    private static Map<String, String> getTenantConfiguration(ConfigParams configParams) {
        return RoleHelper.runAs(configParams.awsRegion, null, configParams.assumeRoleArn,
            dynamoCredentialsProvider -> {
                var configTable = configParams.tenantConfigTable;
                var configTableKey = configParams.tenantConfigTablePartitionKey;
                return DynamoDBHelper.get(configParams.awsRegion, dynamoCredentialsProvider,
                    configTable, configTableKey, configParams.tenantId,
                    configParams.dynamoConfigMap);
            });
    }

    private static void setProperties(Map<String, String> values) {
        properties.putAll(values);
    }
}
