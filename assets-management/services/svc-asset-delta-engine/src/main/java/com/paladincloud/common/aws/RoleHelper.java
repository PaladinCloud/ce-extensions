package com.paladincloud.common.aws;

import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

public class RoleHelper {
    private static final Logger LOGGER = LogManager.getLogger(RoleHelper.class);

    private RoleHelper() {
    }

    public static <T> T runAs(String awsRegion, AwsCredentialsProvider currentCredentialsProvider, String roleArn,
        Function<AwsCredentialsProvider, T> runAsFn) {
        LOGGER.info(STR."Assuming role: \{roleArn} in region \{awsRegion}");
        try (var stsClient = StsClient.builder().region(Region.of(awsRegion)).credentialsProvider(currentCredentialsProvider)
            .build()) {
            var roleRequest = AssumeRoleRequest.builder().roleArn(roleArn)
                .roleSessionName("asset-shipper-session").build();
            var credentialsProvider = StsAssumeRoleCredentialsProvider.builder()
                .stsClient(stsClient).refreshRequest(roleRequest).build();
            return runAsFn.apply(credentialsProvider);
        }
    }
}
