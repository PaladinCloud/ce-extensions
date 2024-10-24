package com.paladincloud.common.assets;

import lombok.Getter;

public enum AssetState {
    MANAGED("managed"),
    UNMANAGED("unmanaged"),
    SUSPICIOUS("suspicious"),
    RECONCILING("reconciling");

    @Getter
    final private String name;

    private AssetState(String name) {
        this.name = name;
    }
}
