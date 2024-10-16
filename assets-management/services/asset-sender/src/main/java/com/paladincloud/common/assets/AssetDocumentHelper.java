package com.paladincloud.common.assets;

import static java.util.Map.entry;

import com.paladincloud.common.AssetDocumentFields;
import com.paladincloud.common.errors.JobException;
import com.paladincloud.common.util.MapHelper;
import com.paladincloud.common.util.StringHelper;
import com.paladincloud.common.util.TimeHelper;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Use the Builder to set properties, and {@link #createFrom(Map)} to convert from a mapped data
 * object to an AssetDTO. Used by @see MergeAssets when processing assets from the mapper.
 */
@Builder
public class AssetDocumentHelper {

    private static final Logger LOGGER = LogManager.getLogger(AssetDocumentHelper.class);
    static private String MAPPER_RAW_DATA = "rawData";

    // These are the fields the primary Asset 2.0 document model fields. This set is to allow
    // a hybrid model that has both the correct top-level fields and the backward compatible
    // top-level fields necessary for some components (policies, for instance).
    // This specific list is used as a filter of the mapper data in order to ignore common fields,
    // which are set elsewhere.
    // Once all components are updated, the additional mapper fields will be removed from the
    // top-level asset document
    static private Set<String> assetFields = new HashSet<>(
        List.of(MAPPER_RAW_DATA,
            AssetDocumentFields.REPORTING_SOURCE, AssetDocumentFields.NAME,
            AssetDocumentFields.ASSET_ID_DISPLAY_NAME,
            AssetDocumentFields.LEGACY_TARGET_TYPE_DISPLAY_NAME,
            AssetDocumentFields.TARGET_TYPE_DISPLAY_NAME, AssetDocumentFields.SOURCE_DISPLAY_NAME,
            AssetDocumentFields.PRIMARY_PROVIDER, AssetDocumentFields.DOC_TYPE,
            AssetDocumentFields.DISCOVERY_DATE, AssetDocumentFields.FIRST_DISCOVERED,
            AssetDocumentFields.LOAD_DATE, AssetDocumentFields.ACCOUNT_ID,
            AssetDocumentFields.ACCOUNT_NAME, AssetDocumentFields.PROJECT_ID,
            AssetDocumentFields.PROJECT_NAME, AssetDocumentFields.SUBSCRIPTION,
            AssetDocumentFields.SUBSCRIPTION_NAME, AssetDocumentFields.CLOUD_TYPE,
            AssetDocumentFields.DOC_ID, AssetDocumentFields.ENTITY, AssetDocumentFields.ENTITY_TYPE,
            AssetDocumentFields.RELATIONS, AssetDocumentFields.LATEST,
            AssetDocumentFields.RESOURCE_GROUP_NAME, AssetDocumentFields.RESOURCE_ID,
            AssetDocumentFields.RESOURCE_NAME, AssetDocumentFields.TAGS));
    static private Map<String, String> accountIdNameMap = new HashMap<>();
    @NonNull
    private ZonedDateTime loadDate;
    @Getter
    @NonNull
    private String idField;
    @NonNull
    private List<String> docIdFields;
    @NonNull
    private String dataSource;
    private boolean isCloud;
    @NonNull
    private String displayName;
    @NonNull
    private String type;
    @NonNull
    private List<Map<String, Object>> tags;
    @NonNull
    private Function<String, String> accountIdToNameFn;
    @NonNull
    private AssetState assetState;
    private String resourceNameField;


    public String buildDocId(Map<String, Object> data) {
        var docId = StringHelper.concatenate(data, docIdFields, "_");
        if ("aws".equalsIgnoreCase(dataSource)) {
            if (docIdFields.contains(AssetDocumentFields.ACCOUNT_ID)) {
                docId = STR."\{StringHelper.indexName(dataSource, type)}_\{docId}";
            }
        }
        if (StringUtils.isBlank(docId)) {
            LOGGER.info(
                STR."docId is not valid: '\{docId}' docIdFields=\{docIdFields} mapper data=\{MapHelper.toJsonString(
                    data)}");
            throw new JobException(
                STR."docId is not valid: '\{docId}', mapper data & config don't match for type=\{type}");
        }
        return docId;
    }

    /**
     * Given mapper data, create an Asset document from it
     *
     * @param data - the mapper created data
     * @return - AssetDTO, intended for manipulation & serialization
     */
    public AssetDTO createFrom(Map<String, Object> data) {
        var idValue = data.getOrDefault(idField, "").toString();
        if (idValue.isEmpty()) {
            return null;
        }

        var docId = buildDocId(data);

        if (!isCloud) {
            throw new JobException("Opinions are not yet supported by the delta engine");
        }

        var dto = new AssetDTO();

        // Set some common properties, which are type safe and require function calls rather
        // than map puts. These properties are removed from 'data' (the mapper data) in order
        // to decrease confusion between an additional property and a typed property.
        Map<String, DtoSetter> fieldSetterMap = Map.ofEntries(
            entry(AssetDocumentFields.ACCOUNT_ID, v -> dto.setAccountId(v.toString())),
            entry(AssetDocumentFields.ACCOUNT_NAME, v -> dto.setAccountName(v.toString())),
            entry(AssetDocumentFields.CLOUD_TYPE,
                v -> dto.setCloudType(v.toString().toLowerCase())),
            entry(AssetDocumentFields.DISCOVERY_DATE,
                v -> dto.setDiscoveryDate(TimeHelper.parseDiscoveryDate(v.toString()))),
            entry(AssetDocumentFields.NAME, v -> dto.setName(v.toString())),
            entry(AssetDocumentFields.SOURCE_DISPLAY_NAME,
                v -> dto.setSourceDisplayName(v.toString())),
            entry(MAPPER_RAW_DATA, v -> dto.setPrimaryProvider(v.toString())),
            entry(AssetDocumentFields.REPORTING_SOURCE, v -> dto.setReportingSource(v.toString())));

        fieldSetterMap.forEach((key, value) -> {
            var fieldValue = getOrNull(key, data);
            if (fieldValue != null) {
                value.set(fieldValue);
            }
        });

        // Set the remaining mapper properties
        dto.setDocId(docId);
        dto.setEntity(true);
        dto.setReportingSource(dataSource);
        dto.setAssetState(assetState);

        // Set common asset properties
        dto.setEntityType(type);
        dto.setLegacyTargetTypeDisplayName(displayName);
        dto.setTargetTypeDisplayName(displayName);
        dto.setDocType(type);

        if (isCloud) {
            dto.addRelation(STR."\{type}\{AssetDocumentFields.RELATIONS}", type);
        }
        dto.setResourceName(data.getOrDefault(resourceNameField, idValue).toString());
        dto.setResourceId(data.getOrDefault(AssetDocumentFields.RESOURCE_ID, idValue).toString());

        if (data.containsKey(AssetDocumentFields.SUBSCRIPTION_NAME)) {
            dto.setAccountName(data.get(AssetDocumentFields.SUBSCRIPTION_NAME).toString());
        } else if (data.containsKey(AssetDocumentFields.PROJECT_NAME)) {
            dto.setAccountName(data.get(AssetDocumentFields.PROJECT_NAME).toString());
        }

        if (data.containsKey(AssetDocumentFields.SUBSCRIPTION)) {
            dto.setAccountId(data.get(AssetDocumentFields.SUBSCRIPTION).toString());
        } else if (data.containsKey(AssetDocumentFields.PROJECT_ID)) {
            dto.setAccountId(data.get(AssetDocumentFields.PROJECT_ID).toString());
        }

        dto.setFirstDiscoveryDate(dto.getDiscoveryDate());

        tags.parallelStream().filter(tag -> MapHelper.containsAll(tag, data, docIdFields))
            .forEach(tag -> {
                var key = tag.get("key").toString();
                if (StringUtils.isNotBlank(key)) {
                    dto.addType(STR."tags.\{key}", tag.get("value"));
                }
            });

        // For CQ Collector accountName will be fetched from RDS using accountId only if not set earlier
        if (("gcp".equalsIgnoreCase(dataSource) || "crowdstrike".equalsIgnoreCase(dataSource))
            && dto.getAccountName() == null) {
            setMissingAccountName(dto, data);
        }

        addTags(data, dto);
        if ("gcp".equalsIgnoreCase(
            data.getOrDefault(AssetDocumentFields.CLOUD_TYPE, "").toString())) {
            addLegacyTags(data, dto);
        }

        if ("Azure".equalsIgnoreCase(dto.getCloudType())) {
            dto.setAssetIdDisplayName(getAssetIdDisplayName(data));
        }

        // Transfer additional mapper provided fields that aren't already set
        data.forEach((key, value) -> {
            if (!assetFields.contains(key)) {
                dto.getAdditionalProperties().put(key, value);
            }
        });

        dto.setLoadDate(loadDate);
        dto.setLatest(true);
        return dto;
    }

    /**
     * Update an existing Asset with fields from the latest mapper data.
     *
     * @param data - the mapper data
     * @param dto  - the existing AssetDTO
     */
    public void updateFrom(Map<String, Object> data, AssetDTO dto) {
        var idValue = data.getOrDefault(idField, "").toString();

        dto.setPrimaryProvider(data.getOrDefault(MAPPER_RAW_DATA, "").toString());
        dto.setSourceDisplayName(
            data.getOrDefault(AssetDocumentFields.SOURCE_DISPLAY_NAME, "").toString());

        // One time only, existing assets in ElasticSearch must be updated to include new fields
        if (StringUtils.isBlank(dto.getReportingSource())) {
            dto.setReportingSource(
                data.getOrDefault(AssetDocumentFields.REPORTING_SOURCE, "").toString());
        }

        dto.setAssetState(assetState);

        // Update all fields the user has control over.
        if (data.containsKey(AssetDocumentFields.NAME)) {
            dto.setName(data.get(AssetDocumentFields.NAME).toString());
        }
        dto.setLoadDate(loadDate);
        dto.setLatest(true);

        dto.setResourceName(data.getOrDefault(resourceNameField, idValue).toString());
        dto.setResourceId(data.getOrDefault(AssetDocumentFields.RESOURCE_ID, idValue).toString());
        if (data.containsKey(AssetDocumentFields.ACCOUNT_NAME)) {
            dto.setAccountName(data.get(AssetDocumentFields.ACCOUNT_NAME).toString());
        }

        if (data.containsKey(AssetDocumentFields.SUBSCRIPTION_NAME)) {
            dto.setAccountName(data.get(AssetDocumentFields.SUBSCRIPTION_NAME).toString());
        } else if (data.containsKey(AssetDocumentFields.PROJECT_NAME)) {
            dto.setAccountName(data.get(AssetDocumentFields.PROJECT_NAME).toString());
        }

        // The display name comes out of our database, but could potentially change with an update.
        // Hence, it gets updated here.
        dto.setLegacyTargetTypeDisplayName(displayName);
        dto.setTargetTypeDisplayName(displayName);
        if ("Azure".equalsIgnoreCase(dto.getCloudType())) {
            dto.setAssetIdDisplayName(getAssetIdDisplayName(data));
        }

        addTags(data, dto);
        if ("gcp".equalsIgnoreCase(
            data.getOrDefault(AssetDocumentFields.CLOUD_TYPE, "").toString())) {
            addLegacyTags(data, dto);
        }

        // Transfer additional mapper provided fields that aren't already set
        data.forEach((key, value) -> {
            if (!assetFields.contains(key)) {
                dto.getAdditionalProperties().put(key, value);
            }
        });

    }

    /**
     * Update AssetDTO fields to indicate the asset has been removed - it's no longer an existing
     * asset.
     *
     * @param dto - the existing AssetDTO that is to be removed.
     */
    public void remove(AssetDTO dto) {
        dto.setLatest(false);
    }

    private Object getOrNull(String key, Map<String, Object> data) {
        if (data.containsKey(key)) {
            return data.get(key);
        }
        return null;
    }

    private String getAssetIdDisplayName(Map<String, Object> data) {
        var resourceGroupName = ObjectUtils.firstNonNull(
            data.get(AssetDocumentFields.RESOURCE_GROUP_NAME), "").toString();
        var assetName = ObjectUtils.firstNonNull(data.get(AssetDocumentFields.NAME), "").toString();
        String assetIdDisplayName;
        if (!resourceGroupName.isEmpty() && !assetName.isEmpty()) {
            assetIdDisplayName = STR."\{resourceGroupName}/\{assetName}";
        } else if (resourceGroupName.isEmpty()) {
            assetIdDisplayName = assetName;
        } else {
            assetIdDisplayName = resourceGroupName;
        }
        return assetIdDisplayName.toLowerCase();
    }

    private void setMissingAccountName(AssetDTO dto, Map<String, Object> data) {
        String accountId = Stream.of(data.get(AssetDocumentFields.PROJECT_ID),
                data.get(AssetDocumentFields.ACCOUNT_ID)).filter(Objects::nonNull).map(String::valueOf)
            .findFirst().orElse(null);
        if (StringUtils.isNotEmpty(accountId)) {
            if (!accountIdNameMap.containsKey(accountId)) {
                var accountName = accountIdToNameFn.apply(accountId);
                if (accountName != null) {
                    accountIdNameMap.put(accountId, accountName);
                }
            }
            dto.setAccountName(accountIdNameMap.get(accountId));
        }
    }

    private void addLegacyTags(Map<String, Object> data, AssetDTO dto) {
        var tagData = data.get(AssetDocumentFields.TAGS);
        if (tagData instanceof Map) {
            @SuppressWarnings("unchecked") var tagMap = (Map<String, Object>) tagData;
            if (!tagMap.isEmpty()) {
                tagMap.forEach((key, value) -> {
                    var firstChar = key.substring(0, 1).toUpperCase();
                    var remainder = key.substring(1);
                    var upperCaseStart = STR."\{firstChar}\{remainder}";
                    dto.addType(STR."\{AssetDocumentFields.asTag(upperCaseStart)}", value);
                });
            }
        }
    }

    private void addTags(Map<String, Object> data, AssetDTO dto) {
        var tagData = data.get(AssetDocumentFields.TAGS);
        if (tagData instanceof Map) {
            dto.setTags((Map<String, String>) tagData);
        }
    }


    interface DtoSetter {

        void set(Object value);
    }
}
