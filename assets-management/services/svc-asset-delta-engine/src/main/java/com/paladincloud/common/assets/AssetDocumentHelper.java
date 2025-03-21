package com.paladincloud.common.assets;

import com.paladincloud.common.AssetDocumentFields;
import com.paladincloud.common.assets.AssetDTO.OpinionCollection;
import com.paladincloud.common.assets.AssetDTO.OpinionItem;
import com.paladincloud.common.errors.JobException;
import com.paladincloud.common.util.MapHelper;
import com.paladincloud.common.util.StringHelper;
import com.paladincloud.common.util.TimeHelper;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
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
    private static boolean warnReportingServiceDisplayName = true;
    // These are the fields the primary Asset 2.0 document model supports. This set is to allow
    // a hybrid model that has both the correct reserved/top-level fields and the backward compatible
    // top-level fields necessary for some components (policies, for instance).
    // This specific list is used as a filter of the mapper data in order to ignore common fields,
    // which are set elsewhere.
    // Once all components are updated, the additional mapper fields will be removed from the
    // top-level asset document
    static private Set<String> assetFields = new HashSet<>(
        List.of(
            MapperFields.IS_ACTIVE,
            MapperFields.ACCOUNT_ID,
            MapperFields.LEGACY_ACCOUNT_ID,
            MapperFields.RAW_DATA,
            MapperFields.REPORTING_SOURCE,
            MapperFields.REPORTING_SERVICE,
            MapperFields.FIRST_SCAN_DATE,
            MapperFields.LAST_SCAN_DATE,
            MapperFields.ENTITY_TYPE_DISPLAY_NAME,

            AssetDocumentFields.LEGACY_NAME,
            AssetDocumentFields.ASSET_ID_DISPLAY_NAME,
            AssetDocumentFields.LEGACY_TARGET_TYPE_DISPLAY_NAME,
            AssetDocumentFields.LEGACY_ENTITY_TYPE_DISPLAY_NAME,
            AssetDocumentFields.LEGACY_SOURCE_DISPLAY_NAME,
            AssetDocumentFields.PRIMARY_PROVIDER,
            AssetDocumentFields.LAST_SCAN_DATE,
            AssetDocumentFields.LEGACY_LAST_SCAN_DATE,
            AssetDocumentFields.FIRST_DISCOVERY_DATE,
            AssetDocumentFields.LEGACY_FIRST_DISCOVERY_DATE,
            AssetDocumentFields.LOAD_DATE,
            AssetDocumentFields.LEGACY_LOAD_DATE,
            AssetDocumentFields.ACCOUNT_ID,
            AssetDocumentFields.LEGACY_ACCOUNT_ID,
            AssetDocumentFields.ACCOUNT_NAME,
            AssetDocumentFields.LEGACY_ACCOUNT_NAME,
            AssetDocumentFields.PROJECT_ID,
            AssetDocumentFields.PROJECT_NAME,
            AssetDocumentFields.SUBSCRIPTION,
            AssetDocumentFields.SUBSCRIPTION_NAME,
            AssetDocumentFields.SOURCE,
            AssetDocumentFields.LEGACY_SOURCE,
            AssetDocumentFields.DOC_TYPE,
            AssetDocumentFields.LEGACY_DOC_TYPE,
            AssetDocumentFields.DOC_ID,
            AssetDocumentFields.LEGACY_DOC_ID,
            AssetDocumentFields.IS_ENTITY,
            AssetDocumentFields.LEGACY_IS_ENTITY,
            AssetDocumentFields.ENTITY_TYPE,
            AssetDocumentFields.LEGACY_ENTITY_TYPE,
            AssetDocumentFields.RELATIONS,
            AssetDocumentFields.IS_LATEST,
            AssetDocumentFields.LEGACY_IS_LATEST,
            AssetDocumentFields.RESOURCE_GROUP_NAME,
            AssetDocumentFields.RESOURCE_ID,
            AssetDocumentFields.LEGACY_RESOURCE_ID,
            AssetDocumentFields.RESOURCE_NAME,
            AssetDocumentFields.LEGACY_RESOURCE_NAME,
            AssetDocumentFields.REGION,
            AssetDocumentFields.TAGS));
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
    @NonNull
    private String displayName;
    @NonNull
    private String type;
    @NonNull
    private List<Map<String, Object>> tags;
    @NonNull
    private Function<String, String> accountIdToNameFn;
    private String resourceNameField;
    private String reportingSource;
    private String reportingSourceService;
    private String reportingSourceServiceDisplayName;
    private AssetState assetState;
    private boolean assetStateServiceEnabled;


    public boolean isPrimarySource() {
        return null == reportingSource || dataSource.equalsIgnoreCase(reportingSource);
    }

    public String buildDocId(Map<String, Object> data) {
        // Allow mappers to override the docId formation.
        var docIdOverride = MapHelper.getFirstOrDefaultString(data,
            List.of(AssetDocumentFields.DOC_ID, AssetDocumentFields.LEGACY_DOC_ID), null);
        if (StringUtils.isNotBlank(docIdOverride)) {
            return docIdOverride;
        }

        if (docIdFields.contains(AssetDocumentFields.LEGACY_ACCOUNT_ID)
            && data.get(AssetDocumentFields.LEGACY_ACCOUNT_ID) == null) {
            data.put(AssetDocumentFields.LEGACY_ACCOUNT_ID,
                data.get(AssetDocumentFields.ACCOUNT_ID));
        }
        var docId = STR."\{dataSource}_\{type}_\{StringHelper.concatenate(data, docIdFields,
            "_")}";
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
        var idValue = getIdValue(data);
        var source = getSource(data);

        var docId = buildDocId(data);
        var dto = new AssetDTO();

        dto.setDocId(docId);
        dto.setDocType(type);

        // Set the remaining asset fields
        if (isPrimarySource()) {
            dto.setSource(source.toLowerCase());
            if (!assetStateServiceEnabled) {
                dto.setAssetState(assetState);
            }
            populateNewPrimary(data, dto, idValue);
        } else {
            populateNewOpinion(data, dto);
        }

        return dto;
    }

    private void populateNewPrimary(Map<String, Object> data, AssetDTO dto, String idValue) {
        dto.setLegacySource(dto.getSource());
        dto.setLegacyDocId(dto.getDocId());
        dto.setLegacyDocType(dto.getDocType());
        if (assetStateServiceEnabled) {
            dto.setAssetState(AssetState.RECONCILING);
        } else {
            dto.setAssetState(assetState);
        }

        setCommonPrimaryFields(data, dto, idValue);

        dto.setPrimaryProvider(data.getOrDefault(MapperFields.RAW_DATA, "").toString());

        dto.setFirstDiscoveryDate(dto.getLastScanDate());
        dto.setLegacyFirstDiscoveryDate(dto.getLastScanDate());

        tags.parallelStream().filter(tag -> MapHelper.containsAll(tag, data, docIdFields))
            .forEach(tag -> {
                var key = tag.get("key").toString();
                if (StringUtils.isNotBlank(key)) {
                    dto.addType(STR."tags.\{key}", tag.get("value"));
                }
            });

        // For CQ Collector, accountName will be fetched from RDS using accountId only if not set earlier
        if (("gcp".equalsIgnoreCase(dataSource) || "crowdstrike".equalsIgnoreCase(
            dataSource))
            && dto.getAccountName() == null) {
            setMissingAccountName(dto, data);
        }

        addTags(data, dto);
        if ("gcp".equalsIgnoreCase(dataSource)) {
            addLegacyTags(data, dto);
        }

        if ("azure".equalsIgnoreCase(dataSource)) {
            dto.setAssetIdDisplayName(getAssetIdDisplayName(data));
        }

        // Transfer additional mapper provided fields that aren't already set
        data.forEach((key, value) -> {
            if (!assetFields.contains(key)) {
                dto.getAdditionalProperties().put(key, value);
            }
        });

        dto.addRelation(STR."\{type}\{AssetDocumentFields.RELATIONS}", type);
    }

    private void populateNewOpinion(Map<String, Object> data, AssetDTO dto) {
        setOpinion(data, dto);
    }

    private void setOpinion(Map<String, Object> data, AssetDTO dto) {
        if (StringUtils.isBlank(reportingSource)) {
            throw new JobException("reportingSource is not set");
        }
        if (StringUtils.isBlank(reportingSourceService)) {
            throw new JobException("reportingService is not set");
        }

        dto.setOpinions(Optional.ofNullable(dto.getOpinions()).orElseGet(OpinionCollection::new));
        var opinionItem = Optional.ofNullable(
                (dto.getOpinions().getSourceAndServiceOpinion(reportingSource, reportingSourceService)))
            .orElseGet(OpinionItem::new);
        opinionItem.setData(
            data.getOrDefault(MapperFields.RAW_DATA, "").toString());
        if (StringUtils.isBlank(reportingSourceServiceDisplayName)) {
            if (warnReportingServiceDisplayName) {
                LOGGER.warn("reportingSourceServiceDisplayName is not set");
            }
            warnReportingServiceDisplayName = false;
        } else {
            opinionItem.setServiceName(reportingSourceServiceDisplayName);
        }
        withValue(data, List.of(MapperFields.FIRST_SCAN_DATE),
            v -> {
                try {
                    opinionItem.setFirstScanDate(TimeHelper.parseISO8601Date(v.toString()));
                } catch (DateTimeParseException e) {
                    LOGGER.error(STR."Failed to parse first scan date: \{v}", e);
                }
            });
        withStringValue(data, List.of(MapperFields.LAST_SCAN_DATE),
            v -> {
                try {
                    opinionItem.setLastScanDate(TimeHelper.parseISO8601Date(v.toString()));
                } catch (DateTimeParseException e) {
                    LOGGER.error(STR."Failed to parse last scan date: \{v}", e);
                }
            });
        withStringValue(data, List.of(MapperFields.OPINION_SERVICE_DEEP_LINK),
            v -> opinionItem.setDeepLink(v.toString()));

        dto.getOpinions().setOpinion(reportingSource, reportingSourceService, opinionItem);
    }

    /**
     * Creates a partial primary asset from a secondary source. This is invoked when the primary
     * asset is not reported but a secondary source reports the asset.
     */
    public AssetDTO createPrimaryFromOpinionData(Map<String, Object> data) {
        var idValue = getIdValue(data);
        var source = getSource(data);

        var dto = new AssetDTO();
        dto.setDocId(buildDocId(data));
        dto.setDocType(type);
        dto.setSource(source);
        dto.setPrimaryProvider("");

        setCommonPrimaryFields(data, dto, idValue);

        dto.setFirstDiscoveryDate(dto.getLastScanDate());
        dto.setLegacyFirstDiscoveryDate(dto.getLastScanDate());
        dto.setLegacySource(dto.getSource());
        dto.setLegacyDocId(dto.getDocId());
        dto.setLegacyDocType(dto.getDocType());

        if (assetStateServiceEnabled) {
            dto.setAssetState(AssetState.RECONCILING);
        } else {
            dto.setAssetState(AssetState.SUSPICIOUS);
        }
        return dto;
    }

    /**
     * Update an existing Asset with fields from the latest mapper data.
     *
     * @param data - the mapper data
     * @param dto  - the existing AssetDTO
     */
    public void updateFrom(Map<String, Object> data, AssetDTO dto) {
        var idValue = getIdValue(data);

        if (isPrimarySource()) {
            updatePrimary(data, dto, idValue);
        } else {
            if (dataSource.equalsIgnoreCase(dto.getSource())) {
                updatePrimaryFromSecondary(data, dto, idValue);
            } else {
                updateOpinion(data, dto);
            }
        }
    }

    private void updatePrimary(Map<String, Object> data, AssetDTO dto, String idValue) {
        dto.setPrimaryProvider(data.getOrDefault(MapperFields.RAW_DATA, "").toString());
        setCommonPrimaryFields(data, dto, idValue);

        // The display name comes out of our database, but could potentially change with an update.
        // Hence, it gets updated here.
        if ("Azure".equalsIgnoreCase(dto.getLegacySource())) {
            dto.setAssetIdDisplayName(getAssetIdDisplayName(data));
        }

        addTags(data, dto);
        if ("gcp".equalsIgnoreCase(
            data.getOrDefault(AssetDocumentFields.LEGACY_SOURCE, "").toString())) {
            addLegacyTags(data, dto);
        }

        // Transfer additional mapper provided fields that aren't already set
        data.forEach((key, value) -> {
            if (!assetFields.contains(key)) {
                dto.getAdditionalProperties().put(key, value);
            }
        });

        // This is needed to upgrade existing documents to the v2 asset model
        if (dto.getDocId() == null || !dto.getDocId().equalsIgnoreCase(dto.getLegacyDocId())) {
            dto.setDocId(dto.getLegacyDocId());
            dto.setDocType(dto.getLegacyDocType());
            dto.setEntityType(dto.getLegacyEntityType());
            dto.setIsEntity(dto.getLegacyIsEntity());
            dto.setIsLatest(dto.getLegacyIsLatest());
            dto.setLastScanDate(dto.getLegacyLastScanDate());
            dto.setLoadDate(dto.getLegacyLoadDate());
            dto.setFirstDiscoveryDate(dto.getLegacyFirstDiscoveryDate());
            dto.setResourceId(dto.getLegacyResourceId());
            dto.setResourceName(dto.getLegacyResourceName());
            dto.setSource(dto.getLegacySource());
            dto.setAccountId(dto.getLegacyAccountId());
            dto.setAccountName(dto.getLegacyAccountName());
        }
    }

    private void updatePrimaryFromSecondary(Map<String, Object> data, AssetDTO dto,
        String idValue) {
        setCommonPrimaryFields(data, dto, idValue);
    }

    private void updateOpinion(Map<String, Object> data, AssetDTO dto) {
        setOpinion(data, dto);
    }

    private void setCommonPrimaryFields(Map<String, Object> data, AssetDTO dto, String idValue) {
        // Set some common properties in a type safe and convenient manner
        withValue(data,
            List.of(AssetDocumentFields.ACCOUNT_ID, AssetDocumentFields.LEGACY_ACCOUNT_ID), v -> {
                dto.setAccountId(v.toString());
                dto.setLegacyAccountId(v.toString());
            });

        withValue(data, List.of(AssetDocumentFields.LAST_SCAN_DATE,
            AssetDocumentFields.LEGACY_LAST_SCAN_DATE), v -> {
            dto.setLastScanDate(TimeHelper.parseDiscoveryDate(v.toString()));
            dto.setLegacyLastScanDate(TimeHelper.parseDiscoveryDate(v.toString()));
        });

        withValue(data, List.of(AssetDocumentFields.REGION), v -> {
            dto.setRegion(v.toString());
        });

        withValue(data,
            List.of(MapperFields.SOURCE_DISPLAY_NAME, MapperFields.LEGACY_SOURCE_DISPLAY_NAME),
            v -> {
                dto.setSourceDisplayName(v.toString());
                dto.setLegacySourceDisplayName(v.toString());
            });

        dto.setIsEntity(true);
        dto.setEntityType(type);
        dto.setEntityTypeDisplayName(displayName);
        dto.setResourceName(data.getOrDefault(resourceNameField, idValue).toString());
        dto.setLoadDate(loadDate);
        dto.setIsLatest(true);
        dto.setIsActive(
            Boolean.parseBoolean(data.getOrDefault(MapperFields.IS_ACTIVE, "true").toString()));

        dto.setLegacyIsEntity(true);
        dto.setLegacyEntityType(type);
        dto.setLegacyEntityTypeDisplayName(displayName);
        dto.setLegacyTargetTypeDisplayName(displayName);
        dto.setLegacyResourceName(dto.getResourceName());
        dto.setLegacyName(dto.getResourceName());
        dto.setLegacyLoadDate(loadDate);
        dto.setLegacyIsLatest(true);

        var resourceId = MapHelper.getFirstOrDefault(data,
            List.of(AssetDocumentFields.RESOURCE_ID, AssetDocumentFields.LEGACY_RESOURCE_ID),
            idValue);
        dto.setResourceId(resourceId.toString());
        dto.setLegacyResourceId(resourceId.toString());

        withValue(data,
            List.of(AssetDocumentFields.ACCOUNT_NAME, AssetDocumentFields.LEGACY_ACCOUNT_NAME,
                AssetDocumentFields.SUBSCRIPTION_NAME, AssetDocumentFields.PROJECT_NAME), v -> {
                dto.setAccountName(v.toString());
                dto.setLegacyAccountName(v.toString());
            });

        var subscription = data.getOrDefault(AssetDocumentFields.SUBSCRIPTION, null);
        var projectId = data.getOrDefault(AssetDocumentFields.PROJECT_ID, null);
        if (subscription != null) {
            dto.setAccountId(subscription.toString());
            dto.setLegacyAccountId(subscription.toString());
        } else if (projectId != null) {
            dto.setAccountId(projectId.toString());
            dto.setLegacyAccountId(projectId.toString());
        }
    }

    /**
     * Update AssetDTO fields to indicate the asset is missing - it's no longer an existing asset.
     * Primary assets are marked as no longer the latest. Stub primary and all opinion documents are
     * deleted if they no longer have any opinions remaining.
     *
     * @param dto - the existing AssetDTO that is missing
     * @return - true if the asset should be deleted, false otherwise.
     */
    public boolean missing(AssetDTO dto) {
        if (isPrimarySource()) {
            dto.setIsLatest(false);
            dto.setLegacyIsLatest(false);
            return false;
        } else {
            // An opinion has been removed; either update the document to reflect that or
            // delete the document.
            dto.getOpinions().removeOpinion(reportingSource, reportingSourceService);
            return !dto.getOpinions().hasOpinions();
        }
    }

    private String getAssetIdDisplayName(Map<String, Object> data) {
        var resourceGroupName = ObjectUtils.firstNonNull(
            data.get(AssetDocumentFields.RESOURCE_GROUP_NAME), "").toString();
        var assetName = ObjectUtils.firstNonNull(data.get(AssetDocumentFields.LEGACY_NAME), "")
            .toString();
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
                data.get(AssetDocumentFields.LEGACY_ACCOUNT_ID)).filter(Objects::nonNull)
            .map(String::valueOf)
            .findFirst().orElse(null);
        if (StringUtils.isNotEmpty(accountId)) {
            if (!accountIdNameMap.containsKey(accountId)) {
                var accountName = accountIdToNameFn.apply(accountId);
                if (accountName != null) {
                    accountIdNameMap.put(accountId, accountName);
                }
            }
            dto.setAccountName(accountIdNameMap.get(accountId));
            dto.setLegacyAccountName(accountIdNameMap.get(accountId));
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

    private String getIdValue(Map<String, Object> data) {
        var idValue = data.getOrDefault(idField, "").toString();
        if (idValue.isEmpty()) {
            throw new JobException(
                STR."Mapper data is missing the designated id field: '\{idField}'");
        }
        return idValue;
    }

    private String getSource(Map<String, Object> data) {
        var source = MapHelper.getFirstOrDefaultString(data,
            List.of(AssetDocumentFields.SOURCE, AssetDocumentFields.LEGACY_SOURCE), null);
        if (source == null) {
            throw new JobException("Mapper data is missing the 'source' field");
        }
        return source;
    }

    private void withValue(Map<String, Object> data, List<String> key,
        Consumer<Object> fn) {
        var fieldValue = MapHelper.getFirstOrDefault(data, key, null);
        if (fieldValue != null) {
            fn.accept(fieldValue);
        }
    }

    private void withStringValue(Map<String, Object> data, List<String> key,
        Consumer<Object> fn) {
        withValue(data, key, v -> {
            var s = v.toString();
            if (!StringUtils.isBlank(s)) {
                fn.accept(s);
            }
        });
    }

    public interface MapperFields {

        String LEGACY_ACCOUNT_ID = "accountid";
        String ACCOUNT_ID = "account_id";

        String RAW_DATA = "rawData";
        String REPORTING_SOURCE = "reporting_source";
        String REPORTING_SERVICE = "reporting_service";

        String ENTITY_TYPE_DISPLAY_NAME = "_entityTypeDisplayName";

        String SOURCE_DISPLAY_NAME = "source_display_name";
        String LEGACY_SOURCE_DISPLAY_NAME = "sourceDisplayName";

        String FIRST_SCAN_DATE = "_first_scan_date";
        String LAST_SCAN_DATE = "_last_scan_date";
        String OPINION_SERVICE_DEEP_LINK = "_deep_link";
        String IS_ACTIVE = "_isActive";
    }
}
