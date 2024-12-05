package com.paladincloud.assetstate;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StartMessage(String jobName, String source, String enricherSource,
                           @JsonProperty("tenant_id") String tenantId,
                           @JsonProperty("tenant_name") String tenantName,
                           String[] assetTypes, Boolean isFromPolicyEngine) {

}
