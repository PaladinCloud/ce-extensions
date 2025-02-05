package com.paladincloud.common.jobs;

import com.paladincloud.common.config.ConfigConstants;
import com.paladincloud.common.config.ConfigConstants.PaladinCloud;
import com.paladincloud.common.config.ConfigParams;
import com.paladincloud.common.config.ConfigService;
import com.paladincloud.common.errors.JobException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class JobExecutor {

    // The required tenant-id is specified as a job argument (which comes from an SQS event)
    private static final String TENANT_ID_JOB_ARGUMENT = "tenant_id";

    // An optional environment variable; it'll be used if provided
    private static final String ASSUME_ROLE_ARN = "ASSUME_ROLE_ARN";

    // An optional environment variable; it's the config key used to get the SQS URL to send the
    // processing done event. It defaults to the asset state service.
    private static final String OUTPUT_TRIGGER_ASSET_STATE = "OUTPUT_TRIGGER_ASSET_STATE";

    // The AWS config details - these are required environment variables
    private static final String REGION = "REGION";
    private static final String SECRET_NAME_PREFIX = "SECRET_NAME_PREFIX";
    private static final String TENANT_CONFIG_OUTPUT_TABLE = "TENANT_CONFIG_OUTPUT_TABLE";
    private static final String TENANT_TABLE_PARTITION_KEY = "TENANT_TABLE_PARTITION_KEY";
    private static final String TENANT_CONFIG_TABLE = "TENANT_CONFIG_TABLE";

    // When set, specifies the ARN to send the message to
    protected static final String OUTPUT_TOPIC_ARN = "OUTPUT_TOPIC_ARN";

    private static final List<String> requiredEnvironmentVariables = List.of(REGION,
        SECRET_NAME_PREFIX, TENANT_CONFIG_OUTPUT_TABLE,
        TENANT_TABLE_PARTITION_KEY, TENANT_CONFIG_TABLE);

    private static final List<String> requiredExecutorFields = List.of(TENANT_ID_JOB_ARGUMENT);

    private static final String ALERT_ERROR_PREFIX = "error occurred in";
    // Provides the query to the config service; a default is used if one isn't given.
    private static final String CONFIG_SERVICE_QUERY = "config-query";
    private static final Logger LOGGER = LogManager.getLogger(JobExecutor.class);
    protected Map<String, String> envVars = new HashMap<>();
    protected Map<String, String> params = new HashMap<>();
    protected String tenantId;

    // These are additional job arguments that are supported:
    //      asset_type_override -   A comma separated list of asset types to use, ignoring what's in the database
    //      index_prefix -          The prefix to use for creating test ElasticSearch indexes
    //      omit_done_event -       if 'true', the final SQS done event will NOT be fired.
    //      skip_asset_count -      If 'true', skip the asset count update

    public void run(String jobName, String[] args) {
        LOGGER.info(STR."Starting \{jobName} \{String.join(" ", args)}");

        var status = "";
        long startTime = System.nanoTime();
        try {
            setDefaultParams();
            envVars.putAll(
                getEnvironmentVariables(List.of(ASSUME_ROLE_ARN, OUTPUT_TOPIC_ARN, OUTPUT_TRIGGER_ASSET_STATE)));
            params.putAll(parseArgs(args));
            validateRequiredFields();

            var assumeRoleArn = envVars.get(ASSUME_ROLE_ARN);

            tenantId = params.get(TENANT_ID_JOB_ARGUMENT);

            var dynamoConfigMap = Map.of("lambda_rule_engine_function_ShipperdoneSQS.id",
                "processing-done-sqs-url",
                "paladincloud_app_gateway_CustomDomain.id", "base-paladincloud-domain",
                "cognito_userpool_PoolDomain.id", "cognito-url-prefix");

            ConfigService.retrieveConfigProperties(
                ConfigParams.builder().assumeRoleArn(assumeRoleArn).tenantId(tenantId)
                    .awsRegion(envVars.get(REGION))
                    .secretNamePrefix(envVars.get(SECRET_NAME_PREFIX))
                    .tenantConfigOutputTable(envVars.get(TENANT_CONFIG_OUTPUT_TABLE))
                    .tenantConfigTablePartitionKey(envVars.get(TENANT_TABLE_PARTITION_KEY))
                    .tenantConfigTable(envVars.get(TENANT_CONFIG_TABLE))
                    .dynamoConfigMap(dynamoConfigMap).build());
            ConfigService.setProperties("param.", params);

            if (ConfigService.isFeatureEnabled("enableAssetStateService")) {
                var outputSQSUrl = envVars.get(OUTPUT_TRIGGER_ASSET_STATE);
                if (StringUtils.isNotBlank(outputSQSUrl)) {
                    ConfigService.setProperties("",
                        Map.of(ConfigConstants.SQS.ASSET_STATE_START_SQS_URL, outputSQSUrl));
                } else {
                    throw new JobException(
                        "feature flag 'enableAssetStateService' enabled, but missing 'OUTPUT_TRIGGER_ASSET_STATE' environment variable (with URL to SQS)");
                }
            }

            LOGGER.info("enableAssetStateService={} completionQueue={}",
                ConfigService.isFeatureEnabled("enableAssetStateService"),
                ConfigService.get(ConfigConstants.SQS.ASSET_STATE_START_SQS_URL));
            var cognitoUrlPrefix = ConfigService.get(PaladinCloud.COGNITO_URL_PREFIX);
            ConfigService.setProperties("", Map.of(PaladinCloud.AUTH_API_URL,
                STR."https://\{cognitoUrlPrefix}.auth.us-east-1.amazoncognito.com"));

            execute();
            status = "Succeeded";
        } catch (Throwable t) {
            status = "Failed";
            LOGGER.error(STR."\{ALERT_ERROR_PREFIX} \{jobName}:", t);
        }

        long duration = System.nanoTime() - startTime;
        long minutes = TimeUnit.NANOSECONDS.toMinutes(duration);
        long seconds = TimeUnit.NANOSECONDS.toSeconds(duration - TimeUnit.MINUTES.toNanos(minutes));
        long milliseconds = TimeUnit.NANOSECONDS.toMillis(
            duration - TimeUnit.MINUTES.toNanos(minutes) - TimeUnit.SECONDS.toNanos(seconds));
        LOGGER.info("Job status: {}; execution time {}", status,
            "%d:%02d.%04d".formatted(minutes, seconds, milliseconds));
    }

    protected abstract void execute();

    protected abstract List<String> getRequiredFields();

    private void setDefaultParams() {
        params.put(CONFIG_SERVICE_QUERY,
            "select targetName,targetConfig,displayName from cf_Target where domain ='Infra & Platforms'");
    }

    private Map<String, String> parseArgs(String[] args) {
        var map = new HashMap<String, String>();
        for (String arg : args) {
            if (StringUtils.isBlank(arg)) {
                continue;
            }

            var tokens = arg.split("=");
            if (tokens.length < 2) {
                throw new JobException(
                    STR."Argument format incorrect: \{arg}; should be '--name=value");
            }
            var keyTokens = tokens[0].split("--");
            map.put(keyTokens[keyTokens.length - 1], tokens[1].trim());
        }
        return map;
    }

    private void validateRequiredFields() {
        var missing = new ArrayList<String>();
        requiredEnvironmentVariables.forEach(field -> {
            if (!envVars.containsKey(field)) {
                missing.add(field);
            }
        });
        requiredExecutorFields.forEach(field -> {
            if (!params.containsKey(field)) {
                missing.add(field);
            }
        });
        getRequiredFields().forEach(field -> {
            if (!params.containsKey(field)) {
                missing.add(field);
            }
        });
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                STR."Missing required field(s): \{String.join(", ", missing)}");
        }
    }

    private Map<String, String> getEnvironmentVariables(List<String> optional) {
        var envVars = new HashMap<String, String>();
        for (var name : requiredEnvironmentVariables) {
            var value = System.getenv(name);
            if (value != null) {
                envVars.put(name, value);
            }
        }
        for (var name : optional) {
            var value = System.getenv(name);
            if (value != null) {
                envVars.put(name, value);
            }
        }
        return envVars;
    }
}
