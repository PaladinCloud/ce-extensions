package com.paladincloud.common.assets;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.paladincloud.common.AssetDocumentFields;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

public class AssetDTO {

    /**
     * Adds the given property and value to fields in this document. Get access to these properties
     * via {@link #getAdditionalProperties()}.
     */
    private final Map<String, Object> additionalProperties = new HashMap<>();

    public boolean isOwner() {
        return cloudType != null && cloudType.equalsIgnoreCase(reportingSource);
    }

    /**
     * This is the unique id for the asset, which depends on the source & type as well as the unique
     * id for the instance. This unique id the same for the lifetime of the asset.
     */
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.DOC_ID)
    private String docId;

    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.DOC_TYPE)
    private String docType;

    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.CSPM_SOURCE)
    private String cspmSource;          // Usually PaladinCloud, but can be Wiz and others

    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.REPORTING_SOURCE)
    private String reportingSource;     // Qualys, Tenable, gcp, aws, azure

    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.CLOUD_TYPE)
    private String cloudType;

    @Getter
    @Setter
    @JsonProperty(AssetDocumentFields.LATEST)
    private boolean latest;

    @Getter
    @Setter
    @JsonProperty(AssetDocumentFields.ENTITY)
    @JsonFormat(shape = Shape.STRING)
    private boolean entity;

    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.ENTITY_TYPE)
    private String entityType;

    /**
     * The date the item was loaded/saved into the repository: The format is "yyyy-MM-dd HH:mm:00Z"
     */
    @Setter
    @Getter
    @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd HH:mm:00Z")
    @JsonProperty(AssetDocumentFields.LOAD_DATE)
    private ZonedDateTime loadDate;

    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.NAME)
    private String name;

    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.RESOURCE_NAME)
    private String resourceName;

    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.RESOURCE_ID)
    private String resourceId;

    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.SOURCE_DISPLAY_NAME)
    private String sourceDisplayName;

    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.PRIMARY_PROVIDER)
    private String primaryProvider;

    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.ASSET_ID_DISPLAY_NAME)
    private String assetIdDisplayName;

    /**
     * This is the legacy field for the target type display name; the field name is
     * all lowercase and is being replaced with `targetTypeDisplayName`. Until the
     * transition is complete, both fields will exist simultaneously.
     */
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.LEGACY_TARGET_TYPE_DISPLAY_NAME)
    private String legacyTargetTypeDisplayName;

    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.TARGET_TYPE_DISPLAY_NAME)
    private String targetTypeDisplayName;

    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.ACCOUNT_ID)
    private String accountId;

    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.ACCOUNT_NAME)
    private String accountName;

    /**
     * The date the primary source discovered this asset; this is set in the mapper. The format is
     * "yyyy-MM-dd HH:mm:00Z"
     */
    @Setter
    @Getter
    @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd HH:mm:00Z")
    @JsonProperty(AssetDocumentFields.DISCOVERY_DATE)
    private ZonedDateTime discoveryDate;

    /**
     * Managed by the asset-shipper; this is the earliest discovery date there are records for. The
     * format is "yyyy-MM-dd HH:mm:00Z"
     */
    @Setter
    @Getter
    @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd HH:mm:00Z")
    @JsonProperty(AssetDocumentFields.FIRST_DISCOVERED)
    private ZonedDateTime firstDiscoveryDate;

    /**
     * This is used by the JSON parser when it can't find a matching field. It's needed for
     * tags & relations
     * @param key - key
     * @param value - value
     */
    @JsonAnySetter
    private void addAdditionalProperty(String key, Object value) {
        additionalProperties.put(key, value);
    }

    public void addRelation(String key, String value) {
        additionalProperties.put(key, value);
    }

    public void addType(String key, Object value) {
        additionalProperties.put(key, value);
    }
    /**
     * This property provides access to the remaining fields in this document. Set non-common
     * properties via {@link #addRelation(String, String)} and {@link #addType(String, Object)}.
     */
    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }
}
