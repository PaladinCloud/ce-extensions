package com.paladincloud.common.assets;

import lombok.Getter;

public enum AssetState {
    MANAGED("Managed"),
    UNMANAGED("Unmanaged"),
    SUSPICIOUS("Suspicious"),
    RECONCILING("Reconciling");

    @Getter
    final private String name;

    private AssetState(String name) {
        this.name = name;
    }
}
