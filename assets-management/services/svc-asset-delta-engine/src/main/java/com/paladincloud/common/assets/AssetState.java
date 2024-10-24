package com.paladincloud.common.assets;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;


@Getter
public enum AssetState {
    MANAGED("managed"),
    UNMANAGED("unmanaged"),
    SUSPICIOUS("suspicious"),
    RECONCILING("reconciling");

    @JsonValue
    private final String name;

    AssetState(String name) {
        this.name = name;
    }
}
