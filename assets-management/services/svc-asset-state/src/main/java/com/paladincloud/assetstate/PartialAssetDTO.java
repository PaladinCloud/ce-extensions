package com.paladincloud.assetstate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PartialAssetDTO {
    @JsonProperty(AssetFieldNames.DOC_ID)
    private String docId;

    @Setter
    @JsonProperty(AssetFieldNames.ASSET_STATE)
    private AssetState assetState;

    @JsonProperty(AssetFieldNames.PRIMARY_PROVIDER)
    private String primaryProvider;
}
