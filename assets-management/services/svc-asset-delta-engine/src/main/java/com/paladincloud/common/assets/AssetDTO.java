package com.paladincloud.common.assets;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.paladincloud.common.AssetDocumentFields;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssetDTO {

    /**
     * Adds the given property and value to fields in this document. Get access to these properties
     * via {@link #getAdditionalProperties()}.
     */
    private final Map<String, Object> additionalProperties = new ConcurrentHashMap<>();
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

    // ---------------------------------------------------------------------------------------------
    // RESERVED FIELDS
    // ---------------------------------------------------------------------------------------------
    // NOTE: There are legacy fields for some of these values; these fields are being replaced
    // for the Asset 2.0 document model. These reserved fields will start with an underscore and
    // be lowerCamelCase (_likeThis).
    // Legacy fields will have the word legacy in them and will eventually be removed.
    @Getter
    @Setter
    @JsonProperty(AssetDocumentFields.ASSET_STATE)
    private AssetState assetState;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.ENTITY_TYPE)
    private String entityType;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.ENTITY_TYPE_DISPLAY_NAME)
    private String entityTypeDisplayName;
    @Getter
    @Setter
    @JsonProperty(AssetDocumentFields.IS_ENTITY)
    private Boolean isEntity;
    @Getter
    @Setter
    @JsonProperty(AssetDocumentFields.IS_LATEST)
    private Boolean isLatest;
    @Getter
    @Setter
    @JsonProperty(AssetDocumentFields.IS_ACTIVE)
    private Boolean isActive;
    /**
     * Managed by the asset-shipper; this is the earliest discovery date there are records for. The
     * format is "yyyy-MM-dd HH:mm:00Z"
     */
    @Setter
    @Getter
    @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd HH:mm:00Z")
    @JsonProperty(AssetDocumentFields.FIRST_DISCOVERY_DATE)
    private ZonedDateTime firstDiscoveryDate;
    /**
     * The most recent date the primary source discovered this asset; this is set in the mapper. The
     * format is "yyyy-MM-dd HH:mm:00Z"
     */
    @Setter
    @Getter
    @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd HH:mm:00Z")
    @JsonProperty(AssetDocumentFields.LAST_SCAN_DATE)
    private ZonedDateTime lastScanDate;
    /**
     * The date the item was loaded/saved into the repository: The format is "yyyy-MM-dd HH:mm:00Z"
     */
    @Setter
    @Getter
    @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd HH:mm:00Z")
    @JsonProperty(AssetDocumentFields.LOAD_DATE)
    private ZonedDateTime loadDate;
    // ---------------------------------------------------------------------------------------------
    // LEGACY RESERVED FIELDS
    // ---------------------------------------------------------------------------------------------
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.LEGACY_DOC_ID)
    private String legacyDocId;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.LEGACY_DOC_TYPE)
    private String legacyDocType;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.LEGACY_ENTITY_TYPE)
    private String legacyEntityType;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.LEGACY_ENTITY_TYPE_DISPLAY_NAME)
    private String legacyEntityTypeDisplayName;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.LEGACY_TARGET_TYPE_DISPLAY_NAME)
    private String legacyTargetTypeDisplayName;
    @Getter
    @Setter
    @JsonProperty(AssetDocumentFields.LEGACY_IS_ENTITY)
    @JsonFormat(shape = Shape.STRING)
    private Boolean legacyIsEntity;
    @Getter
    @Setter
    @JsonProperty(AssetDocumentFields.LEGACY_IS_LATEST)
    private Boolean legacyIsLatest;
    @Setter
    @Getter
    @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd HH:mm:00Z")
    @JsonProperty(AssetDocumentFields.LEGACY_LAST_SCAN_DATE)
    private ZonedDateTime legacyLastScanDate;
    @Setter
    @Getter
    @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd HH:mm:00Z")
    @JsonProperty(AssetDocumentFields.LEGACY_LOAD_DATE)
    private ZonedDateTime legacyLoadDate;
    @Setter
    @Getter
    @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd HH:mm:00Z")
    @JsonProperty(AssetDocumentFields.LEGACY_FIRST_DISCOVERY_DATE)
    private ZonedDateTime legacyFirstDiscoveryDate;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.RESOURCE_ID)
    private String resourceId;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.RESOURCE_NAME)
    private String resourceName;

    // ---------------------------------------------------------------------------------------------
    // TOP LEVEL FIELDS
    // ---------------------------------------------------------------------------------------------
    // NOTE: There are legacy fields for some of these values; these fields are being replaced
    // for the Asset 2.0 document model. These reserved fields are snake case (like_this).
    // Legacy fields will have the word legacy in them and will eventually be removed.
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.SOURCE)
    private String source;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.SOURCE_DISPLAY_NAME)
    private String sourceDisplayName;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.ACCOUNT_ID)
    private String accountId;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.ACCOUNT_NAME)
    private String accountName;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.REGION)
    private String region;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.TAGS)
    private Map<String, String> tags;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.PRIMARY_PROVIDER)
    private String primaryProvider;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.OPINIONS)
    @JsonUnwrapped
    private OpinionCollection opinions;

    // ---------------------------------------------------------------------------------------------
    // LEGACY TOP LEVEL FIELDS
    // ---------------------------------------------------------------------------------------------
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.LEGACY_SOURCE_DISPLAY_NAME)
    private String legacySourceDisplayName;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.LEGACY_RESOURCE_ID)
    private String legacyResourceId;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.LEGACY_NAME)
    private String legacyName;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.LEGACY_RESOURCE_NAME)
    private String legacyResourceName;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.LEGACY_SOURCE)
    private String legacySource;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.LEGACY_ACCOUNT_ID)
    private String legacyAccountId;
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.LEGACY_ACCOUNT_NAME)
    private String legacyAccountName;
    // NOTE: This is only set for Azure
    @Setter
    @Getter
    @JsonProperty(AssetDocumentFields.ASSET_ID_DISPLAY_NAME)
    private String assetIdDisplayName;

    /**
     * This is used by the JSON parser when it can't find a matching field. It's needed for tags &
     * relations
     *
     * @param key   - key
     * @param value - value
     */
    @JsonAnySetter
    private void addAdditionalProperty(String key, Object value) {
        if (!key.equals(AssetDocumentFields.OPINIONS)) {
            additionalProperties.put(key, value);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Fields of uncertain usage; it's not clear if these continue to be used; these should be
    // either proper top-level fields or legacy fields or be removed.

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

    @JsonSerialize(as = OpinionCollection.class)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class OpinionCollection {

        private final Map<String, Map<String, OpinionItem>> opinions = new HashMap<>();

        public void setOpinion(String reportingSource, String reportingService,
            OpinionItem opinionItem) {
            opinions.computeIfAbsent(reportingSource, k -> new HashMap<>())
                .put(reportingService, opinionItem);
        }

        public boolean hasOpinions() {
            return !opinions.isEmpty();
        }

        public void removeOpinion(String reportingSource, String reportingService) {
            var sourceOpinions = opinions.get(reportingSource);
            if (sourceOpinions != null) {
                sourceOpinions.remove(reportingService);
                if (sourceOpinions.isEmpty()) {
                    opinions.remove(reportingSource);
                }
            }
        }

        public OpinionItem getSourceAndServiceOpinion(String reportingSource, String reportingService) {
            var sourceOpinions = opinions.get(reportingSource);
            if (sourceOpinions != null) {
                return sourceOpinions.get(reportingService);
            }
            return new OpinionItem();
        }
    }

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OpinionItem {

        @JsonProperty("data")
        private String data;

        @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:00'Z'", timezone = "UTC")
        @JsonProperty("firstScanDate")
        private ZonedDateTime firstScanDate;

        @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:00'Z'", timezone = "UTC")
        @JsonProperty("lastScanDate")
        private ZonedDateTime lastScanDate;

        @JsonProperty("serviceName")
        private String serviceName;

        @JsonProperty("deepLink")
        private String deepLink;
    }
}
