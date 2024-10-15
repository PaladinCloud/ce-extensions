package com.paladincloud.common.config;

import java.util.Map;
import lombok.Builder;
import lombok.NonNull;

/**
 * This class provides the parameters for the ConfigService - it uses this info to get the URL
 * and credentials needed to call the service
 */
@Builder
public class ConfigParams {

    // The role to assume to get both secrets & config from DynamoDB
    @NonNull
    String assumeRoleArn;

    // The tenantId (GUID)
    @NonNull
    String tenantId;

    // The AWS Region for the secrets & config
    @NonNull
    String awsRegion;

    // The prefix of the secret name
    @NonNull
    String secretNamePrefix;

    // The DynamoDB table with tenant config
    @NonNull
    String tenantConfigTable;

    // The partition key for the DynamoDB tenant config table
    @NonNull
    String tenantConfigTablePartitionKey;

    // The map of DynamoDB config to retrieve. The key is the DynamoDB config and the
    // value is the name to use for storing in the config properties.
    @NonNull
    Map<String, String> dynamoConfigMap;
}
