package com.paladincloud.common.aws;

import com.paladincloud.common.errors.JobException;
import com.paladincloud.common.util.JsonHelper;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import com.paladincloud.common.config.ConfigService;

public class LambdaInvoker {

    private static final Logger LOGGER = LogManager.getLogger(LambdaInvoker.class);
    private static final String LAMBDA_NAME_SUFFIX = "-svc-tagging-compliance-summary-lambda";
    private static final String INTERNAL_STACK_NAME = "internal_stack_name";

    private static String cachedInternalStackName;

    private LambdaInvoker() {
    }

    private static String getRegion() {
        return System.getenv("REGION");
    }

    private static String getTenantId() {
        return ConfigService.get("param.tenant_id");
    }

    private static String getAssumeRoleArn() {
        return System.getenv("ASSUME_ROLE_ARN");
    }

    public static String getInternalStackName() throws Exception {
        if (cachedInternalStackName != null) {
            return cachedInternalStackName;
        }

        var region = getRegion();
        var assumeRoleArn = getAssumeRoleArn();
        var tenantId = getTenantId();
        var partitionKey = System.getenv("TENANT_TABLE_PARTITION_KEY");
        var tenantConfigTable = System.getenv("TENANT_CONFIG_TABLE");

        cachedInternalStackName = RoleHelper.runAs(region, null, assumeRoleArn,
                credentialsProvider -> DynamoDBHelper.get(region, credentialsProvider,
                                tenantConfigTable, partitionKey, tenantId,
                                Map.of(INTERNAL_STACK_NAME, INTERNAL_STACK_NAME))
                        .get(INTERNAL_STACK_NAME));

        if (cachedInternalStackName == null || cachedInternalStackName.isEmpty()) {
            throw new Exception(
                    STR."internal_stack_name not found in \{tenantConfigTable} for tenant: \{tenantId}");
        }

        LOGGER.info("Retrieved internal_stack_name: {}", cachedInternalStackName);
        return cachedInternalStackName;
    }

    public static String invokeTaggingSummaryLambda(String ag) throws Exception {
        var tenantId = getTenantId();
        var internalStackName = getInternalStackName();
        var lambdaName = internalStackName + LAMBDA_NAME_SUFFIX;
        var region = getRegion();
        var assumeRoleArn = getAssumeRoleArn();
        var mapper = JsonHelper.objectMapper;

        var http = mapper.createObjectNode();
        http.put("method", "POST");
        http.put("protocol", "HTTP/1.1");

        var lambdaTenant = mapper.createObjectNode();
        lambdaTenant.put("tenantId", tenantId);

        var authorizer = mapper.createObjectNode();
        authorizer.set("lambda", lambdaTenant);

        var requestContext = mapper.createObjectNode();
        requestContext.set("http", http);
        requestContext.set("authorizer", authorizer);

        var headers = mapper.createObjectNode();
        headers.put("accept", "application/json");
        headers.put("content-type", "application/json");

        var body = mapper.createObjectNode();
        body.put("ag", ag);

        var payload = mapper.createObjectNode();
        payload.set("requestContext", requestContext);
        payload.put("rawPath", "api/v2/compliance/tagging-summary");
        payload.set("queryStringParameters", mapper.createObjectNode());
        payload.set("headers", headers);
        payload.put("body", body.toString());

        LOGGER.info("Invoking Lambda: {} for ag: {}", lambdaName, ag);

        return RoleHelper.runAs(region, null, assumeRoleArn, credentialsProvider -> {
            var builder = LambdaClient.builder().region(Region.of(region));
            if (credentialsProvider != null) {
                builder.credentialsProvider(credentialsProvider);
            }
            try (var lambdaClient = builder.build()) {
                var invokeRequest = InvokeRequest.builder()
                        .functionName(lambdaName)
                        .payload(SdkBytes.fromUtf8String(payload.toString()))
                        .build();

                var result = lambdaClient.invoke(invokeRequest);
                var rawResponse = result.payload().asUtf8String();

                if (result.functionError() != null && !result.functionError().isEmpty()) {
                    LOGGER.error("Lambda function error: {}, response: {}",
                            result.functionError(), rawResponse);
                    throw new JobException(
                            STR."Lambda invocation failed for \{lambdaName}: \{rawResponse}");
                }

                LOGGER.info("Lambda response status code: {}", result.statusCode());

                Map<String, Object> responseObj = JsonHelper.mapFromString(rawResponse);
                var statusCodeObj = responseObj.get("statusCode");
                if (statusCodeObj != null) {
                    int statusCode = ((Number) statusCodeObj).intValue();
                    if (statusCode != 200) {
                        LOGGER.warn("Lambda returned non-200 status: {}, response: {}",
                                statusCode, rawResponse);
                    }
                }

                var responseBody = responseObj.get("body");
                return responseBody != null ? responseBody.toString() : rawResponse;
            } catch (JobException e) {
                throw e;
            } catch (Exception e) {
                throw new JobException("Error invoking tagging summary Lambda", e);
            }
        });
    }
}