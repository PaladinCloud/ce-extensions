package com.paladincloud.common.jobs;

import com.paladincloud.common.config.ConfigConstants;
import com.paladincloud.common.config.ConfigParams;
import com.paladincloud.common.config.Configuration;
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

    private static final List<String> requiredExecutorFields = List.of(TENANT_ID_JOB_ARGUMENT);

    private static final String ALERT_ERROR_PREFIX = "error occurred in";
    // Provides the query to the config service; a default is used if one isn't given.
    private static final Logger LOGGER = LogManager.getLogger(JobExecutor.class);
    protected Map<String, String> envVars = new HashMap<>();
    protected Map<String, String> params = new HashMap<>();
    protected String tenantId;
    protected String tenantName;

    public void run(String jobName, String[] args) {
        LOGGER.info(STR."Starting \{jobName} \{String.join(" ", args)}");

        var status = "";
        long startTime = System.nanoTime();
        try {
            var allOptional = new ArrayList<>(optionalEnvironmentVariables);
            allOptional.addAll(getOptionalFields());
            envVars.putAll(getEnvironmentVariables(allOptional));
            params.putAll(parseArgs(args));
            validateRequiredFields();

            var assumeRoleArn = envVars.get(ASSUME_ROLE_ARN);

            tenantId = params.get(TENANT_ID_JOB_ARGUMENT);

            var dynamoConfigMap = Map.of(ConfigConstants.TENANT_NAME, ConfigConstants.TENANT_NAME,
                "datastore_es_ESDomain.endpoint", ConfigConstants.ELASTICSEARCH_HOST);

            Configuration.retrieveConfiguration(
                ConfigParams.builder().assumeRoleArn(assumeRoleArn).tenantId(tenantId)
                    .awsRegion(envVars.get(REGION))
                    .secretNamePrefix(envVars.get(SECRET_NAME_PREFIX))
                    .tenantConfigTable(envVars.get(TENANT_CONFIG_OUTPUT_TABLE))
                    .tenantConfigTablePartitionKey(envVars.get(TENANT_TABLE_PARTITION_KEY))
                    .dynamoConfigMap(dynamoConfigMap).build());

            tenantName = Configuration.get(ConfigConstants.TENANT_NAME);

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

    protected abstract List<String> getOptionalFields();

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
            map.put(keyTokens[keyTokens.length - 1], tokens[1]);
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
