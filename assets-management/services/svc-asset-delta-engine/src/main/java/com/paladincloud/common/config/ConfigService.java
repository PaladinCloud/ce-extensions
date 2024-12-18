package com.paladincloud.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paladincloud.common.aws.DynamoDBHelper;
import com.paladincloud.common.aws.RoleHelper;
import com.paladincloud.common.config.ConfigConstants.PaladinCloud;
import com.paladincloud.common.errors.JobException;
import com.paladincloud.common.util.HttpHelper;
import com.paladincloud.common.util.HttpHelper.AuthorizationType;
import com.paladincloud.common.util.JsonHelper;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

public class ConfigService {

    private static final Logger LOGGER = LogManager.getLogger(ConfigService.class);
    private static final Properties properties = new Properties();
    private static final String CONFIG_SERVICE_API_PATH = "/config/batch/prd/latest";

    private ConfigService() {
    }

    public static String get(String propertyName) {
        return get(propertyName, null);
    }

    public static String get(String propertyName, String defaultValue) {
        return properties.getProperty(propertyName, defaultValue);
    }

    public static boolean isFeatureEnabled(String feature) {
        return "true".equalsIgnoreCase(properties.getProperty("feature_flags." + feature, "false"));
    }

    /**
     * Retrieve configuration properties from the given config service, making them available in the
     * properties field.
     * <p></p>
     * NOTE: Retrieved properties are prefixed, ie, 'elastic-search.host', which comes from the
     * 'batch' source, becomes 'batch.elastic-search.host'
     *
     * @param configParams - the parameters used to retrieve configuration
     */
    public static void retrieveConfigProperties(ConfigParams configParams) {
        if (!properties.isEmpty()) {
            return;
        }

        var tenantFeatureFlags = getTenantFeatureFlags(configParams);
        LOGGER.info("feature flags={}", tenantFeatureFlags);
        ConfigService.setProperties("", tenantFeatureFlags);

        var dynamoConfig = getTenantOutputConfiguration(configParams);
        ConfigService.setProperties("config.", dynamoConfig);

        var basePaladinApiUrl = STR."https://\{dynamoConfig.get("base-paladincloud-domain")}/api";
        properties.put(ConfigConstants.PaladinCloud.BASE_PALADIN_CLOUD_API_URI, basePaladinApiUrl);

        var secretsMap = getSecretsWithRole(configParams);
        var configCredentials = secretsMap.get("CONFIG_CREDENTIALS");

        fetchConfigProperties(properties.getProperty(PaladinCloud.BASE_PALADIN_CLOUD_API_URI)
            + CONFIG_SERVICE_API_PATH, configCredentials);
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

    private static Map<String, String> getTenantOutputConfiguration(ConfigParams configParams) {
        return RoleHelper.runAs(configParams.awsRegion, null, configParams.assumeRoleArn,
            dynamoCredentialsProvider -> {
                var configTable = configParams.tenantConfigOutputTable;
                var configTableKey = configParams.tenantConfigTablePartitionKey;
                return DynamoDBHelper.get(configParams.awsRegion, dynamoCredentialsProvider,
                    configTable, configTableKey, configParams.tenantId,
                    configParams.dynamoConfigMap);
            });
    }

    private static Map<String, String> getTenantFeatureFlags(ConfigParams configParams) {
        return RoleHelper.runAs(configParams.awsRegion, null, configParams.assumeRoleArn,
            dynamoCredentialsProviders -> {
                var table = configParams.tenantConfigTable;
                var tableKey = configParams.tenantConfigTablePartitionKey;
                return DynamoDBHelper.get(configParams.awsRegion, dynamoCredentialsProviders,
                    table, tableKey, configParams.tenantId,
                    Map.of("tenant_feature_flags.enableAssetStateService.status", "feature_flags.enableAssetStateService"));
            });
    }

    public static void setProperties(String prefix, Map<String, String> values) {
        for (var entry : values.entrySet()) {
            properties.put(prefix + entry.getKey(), entry.getValue());
        }
    }

    private static void fetchConfigProperties(String uri, String credentials) {
        var headers = HttpHelper.getBasicHeaders(AuthorizationType.BASIC, credentials);
        try {
            var configJson = HttpHelper.get(uri, headers);
            var objectMapper = new ObjectMapper().configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            var configResponse = objectMapper.readValue(configJson, ConfigApiResponse.class);
            for (var source : configResponse.propertySources) {
                String prefix = null;
                if (source.name.contains("application")) {
                    prefix = "application";
                } else if (source.name.contains("batch")) {
                    prefix = "batch";
                }
                if (prefix != null) {
                    for (var entry : source.source.entrySet()) {
                        properties.put(STR."\{prefix}.\{entry.getKey()}", entry.getValue());
                    }
                }
            }
        } catch (Exception e) {
            throw new JobException("Unable to get configuration properties", e);
        }
    }
}
