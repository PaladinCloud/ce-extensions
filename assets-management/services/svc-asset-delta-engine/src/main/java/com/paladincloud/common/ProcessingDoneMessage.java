package com.paladincloud.common;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProcessingDoneMessage(String jobName, String source, String enricherSource,
                                    @JsonProperty("tenant_id") String tenantId,
                                    @JsonProperty("tenant_name") String tenantName) {

}
