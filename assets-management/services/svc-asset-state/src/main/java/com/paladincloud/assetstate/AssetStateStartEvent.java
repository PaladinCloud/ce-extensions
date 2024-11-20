package com.paladincloud.assetstate;

public record AssetStateStartEvent(String tenantId, String dataSource, String[] assetTypes, boolean isFromPolicyEngine) {

    public String toCommandLine() {
        return String.format("--tenant_id=%s --data_source=%s --asset_types=%s --is_from_policy_engine=%s",
            tenantId, dataSource, String.join(",", assetTypes), isFromPolicyEngine);
    }
}
