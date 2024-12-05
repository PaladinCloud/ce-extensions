package com.paladincloud.assetstate;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PolicyEngineStartEvent(String jobName, String source, String enricherSource,
                                     String[] assetTypes, @JsonProperty("tenant_id") String tenantId,
                                     @JsonProperty("tenant_name") String tenantName) {

}
