package com.paladincloud.common.jobs;

import com.paladincloud.assetstate.StartMessage;
import com.paladincloud.common.config.ConfigConstants;
import com.paladincloud.common.config.ConfigParams;
import com.paladincloud.common.config.Configuration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class JobExecutor {

    // The required tenant-id is specified as a job argument (which comes from an SQS event)
    private static final String TENANT_ID = "tenant_id";

    // Optional environment variables; they'll be used if provided
    private static final String ASSUME_ROLE_ARN = "ASSUME_ROLE_ARN";
    private static final String REGION = "REGION";
    private static final List<String> optionalEnvironmentVariables = List.of(ASSUME_ROLE_ARN,
        REGION);

    // The AWS config details - these are required environment variables
    private static final String SECRET_NAME_PREFIX = "SECRET_NAME_PREFIX";
    private static final String TENANT_CONFIG_OUTPUT_TABLE = "TENANT_CONFIG_OUTPUT_TABLE";
    private static final String TENANT_TABLE_PARTITION_KEY = "TENANT_TABLE_PARTITION_KEY";

    private static final List<String> requiredEnvironmentVariables = List.of(REGION,
        SECRET_NAME_PREFIX, TENANT_CONFIG_OUTPUT_TABLE,
        TENANT_TABLE_PARTITION_KEY);

    private static final String ALERT_ERROR_PREFIX = "error occurred in";
    // Provides the query to the config service; a default is used if one isn't given.
    private static final Logger LOGGER = LogManager.getLogger(JobExecutor.class);
    protected Map<String, String> envVars = new HashMap<>();
    protected String tenantId;

    public void run(String jobName, StartMessage startMessage) {
        LOGGER.info(String.format("Starting %s %s", jobName, startMessage));

        var status = "";
        long startTime = System.nanoTime();
        try {
            envVars.putAll(getEnvironmentVariables(optionalEnvironmentVariables));

            validateRequiredFields();

            var assumeRoleArn = envVars.get(ASSUME_ROLE_ARN);

            tenantId = startMessage.tenantId();

            var dynamoConfigMap = Map.of("tenant_name", "tenant_name",
                "datastore_es_ESDomain.endpoint", ConfigConstants.ELASTICSEARCH_HOST,
                "lambda_rule_engine_function_ShipperdoneSQS.id", ConfigConstants.SHIPPER_DONE_URL);

            Configuration.retrieveConfiguration(
                ConfigParams.builder().assumeRoleArn(assumeRoleArn).tenantId(tenantId)
                    .awsRegion(envVars.get(REGION))
                    .secretNamePrefix(envVars.get(SECRET_NAME_PREFIX))
                    .tenantConfigTable(envVars.get(TENANT_CONFIG_OUTPUT_TABLE))
                    .tenantConfigTablePartitionKey(envVars.get(TENANT_TABLE_PARTITION_KEY))
                    .dynamoConfigMap(dynamoConfigMap).build());

            execute(startMessage);
            status = "Succeeded";
        } catch (Throwable t) {
            status = "Failed";
            LOGGER.error(String.format("%s %s:", ALERT_ERROR_PREFIX, jobName), t);
        }

        long duration = System.nanoTime() - startTime;
        long minutes = TimeUnit.NANOSECONDS.toMinutes(duration);
        long seconds = TimeUnit.NANOSECONDS.toSeconds(duration - TimeUnit.MINUTES.toNanos(minutes));
        long milliseconds = TimeUnit.NANOSECONDS.toMillis(
            duration - TimeUnit.MINUTES.toNanos(minutes) - TimeUnit.SECONDS.toNanos(seconds));
        LOGGER.info("Job status: {}; execution time {}", status,
            "%d:%02d.%04d".formatted(minutes, seconds, milliseconds));
    }

    protected abstract void execute(StartMessage startMessage);

    private void validateRequiredFields() {
        var missing = new ArrayList<String>();
        requiredEnvironmentVariables.forEach(field -> {
            if (!envVars.containsKey(field)) {
                missing.add(field);
            }
        });
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                String.format("Missing required field(s): %s", String.join(", ", missing)));
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
