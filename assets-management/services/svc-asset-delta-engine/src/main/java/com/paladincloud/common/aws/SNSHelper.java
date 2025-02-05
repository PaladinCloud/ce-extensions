package com.paladincloud.common.aws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paladincloud.common.errors.JobException;
import javax.inject.Inject;
import javax.inject.Singleton;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;

@Singleton
public class SNSHelper {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public SNSHelper() {
    }

    public <T> String sendMessage(String snsArn, T message, String messageGroupId) {
        String snsMessage;
        try {
            snsMessage = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new JobException("Failed sending message: unable to transform message", e);
        }

        return internalSendMessage(snsArn, snsMessage, messageGroupId);
    }

    private String internalSendMessage(String snsArn, String message, String messageGroupId) {
        var request = PublishRequest.builder()
            .message(message)
            .topicArn(snsArn)
            .messageGroupId(messageGroupId)
            .messageDeduplicationId(messageGroupId)
            .build();
        try {
            var result = SnsClient.builder().build().publish(request);
            return result.messageId();
        } catch (SnsException e) {
            throw new JobException("Failed sending SNS message", e);
        }

    }
}
