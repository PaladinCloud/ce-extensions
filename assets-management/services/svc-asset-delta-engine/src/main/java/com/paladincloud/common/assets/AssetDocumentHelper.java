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
import java.util.stream.Collectors;
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
    // These are the fields the primary Asset 2.0 document model supports. This set is to allow
    // a hybrid model that has both the correct reserved/top-level fields and the backward compatible
    // top-level fields necessary for some components (policies, for instance).
    // This specific list is used as a filter of the mapper data in order to ignore common fields,
    // which are set elsewhere.
    // Once all components are updated, the additional mapper fields will be removed from the
    // top-level asset document
    static private Set<String> assetFields = new HashSet<>(
        List.of(
            MapperFields.ACCOUNT_ID,
            MapperFields.LEGACY_ACCOUNT_ID,
            MapperFields.RAW_DATA,
            MapperFields.REPORTING_SOURCE,

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
    private String reportingSource;
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
    private String opinionSource;
    private String opinionService;


    public boolean isPrimarySource() {
        return null == opinionSource || reportingSource.equalsIgnoreCase(opinionSource);
    }

    public String buildDocId(Map<String, Object> data) {
        var docId = STR."\{reportingSource}_\{type}_\{StringHelper.concatenate(data, docIdFields,
            "_")}";
        if ("aws".equalsIgnoreCase(reportingSource)) {
            if (docIdFields.contains(AssetDocumentFields.LEGACY_ACCOUNT_ID)) {
                docId = STR."\{StringHelper.indexName(reportingSource, type)}_\{docId}";
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
            throw new JobException(
                STR."Mapper data is missing the designated id field: '\{idField}'");
        }

        var source = MapHelper.getFirstOrDefaultString(data,
            List.of(AssetDocumentFields.SOURCE, AssetDocumentFields.LEGACY_SOURCE), null);
        if (source == null) {
            throw new JobException("Mapper data is missing the 'source' field");
        }

        var reportingSource = MapHelper.getFirstOrDefaultString(data,
            List.of(MapperFields.REPORTING_SOURCE), null);
        if (reportingSource == null) {
            LOGGER.error(
                "Mapper data is missing the 'reporting_source' field, assuming it's the same as the source");
            reportingSource = source;
        }

        var docId = buildDocId(data);
        var dto = new AssetDTO();

        dto.setDocId(docId);
        dto.setDocType(type);

        // Set the remaining asset fields
        if (isPrimarySource()) {
            dto.setSource(reportingSource.toLowerCase());
            dto.setAssetState(assetState);
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
        if (("gcp".equalsIgnoreCase(reportingSource) || "crowdstrike".equalsIgnoreCase(
            reportingSource))
            && dto.getAccountName() == null) {
            setMissingAccountName(dto, data);
        }

        addTags(data, dto);
        if ("gcp".equalsIgnoreCase(
            data.getOrDefault(AssetDocumentFields.LEGACY_SOURCE, "").toString())) {
            addLegacyTags(data, dto);
        }

        if ("Azure".equalsIgnoreCase(dto.getLegacySource())) {
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
        if (StringUtils.isBlank(opinionSource)) {
            throw new JobException("opinionSource is not set");
        }
        if (StringUtils.isBlank(opinionService)) {
            throw new JobException("opinionService is not set");
        }
        if (dto.getOpinions() == null) {
            dto.setOpinions(new HashMap<>());
        }

        Map<String, String> reportingOpinions = dto.getOpinions().get(opinionSource);
        if (reportingOpinions == null) {
            reportingOpinions = new HashMap<>();
        } else {
            reportingOpinions = new HashMap<>(reportingOpinions);
        }
        reportingOpinions.put(opinionService,
            data.getOrDefault(MapperFields.RAW_DATA, "").toString());
        var opinions = new HashMap<>(dto.getOpinions());
        opinions.put(opinionSource, reportingOpinions);
        dto.setOpinions(opinions);
    }

    /**
     * Creates a partial primary asset from a secondary source. This is invoked when the primary
     * asset is not reported but a secondary source reports the asset.
     */
    public AssetDTO createPrimaryFromOpinionData(Map<String, Object> data) {
        var idValue = data.getOrDefault(idField, "").toString();
        if (idValue.isEmpty()) {
            throw new JobException(
                STR."Mapper data is missing the designated id field: '\{idField}'");
        }

        var source = MapHelper.getFirstOrDefaultString(data,
            List.of(AssetDocumentFields.SOURCE, AssetDocumentFields.LEGACY_SOURCE), null);
        if (source == null) {
            throw new JobException("Mapper data is missing the 'source' field");
        }

        var reportingSource = MapHelper.getFirstOrDefaultString(data,
            List.of(MapperFields.REPORTING_SOURCE), null);
        if (reportingSource == null) {
            LOGGER.error(
                "Mapper data is missing the 'reporting_source' field, assuming it's the same as the source");
            reportingSource = source;
        }

        var dto = new AssetDTO();
        dto.setDocId(buildDocId(data));
        dto.setDocType(type);
        dto.setSource(reportingSource.toLowerCase());

        setCommonPrimaryFields(data, dto, idValue);

        dto.setFirstDiscoveryDate(dto.getLastScanDate());
        dto.setLegacyFirstDiscoveryDate(dto.getLastScanDate());
        dto.setLegacySource(dto.getSource());
        dto.setLegacyDocId(dto.getDocId());
        dto.setLegacyDocType(dto.getDocType());

        dto.setAssetState(AssetState.SUSPICIOUS);
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
        if (idValue.isEmpty()) {
            throw new JobException(
                STR."Mapper data is missing the designated id field: '\{idField}'");
        }

        var source = MapHelper.getFirstOrDefaultString(data,
            List.of(AssetDocumentFields.SOURCE, AssetDocumentFields.LEGACY_SOURCE), null);
        if (source == null) {
            throw new JobException("Mapper data is missing the 'source' field");
        }

        if (isPrimarySource()) {
            updatePrimary(data, dto, idValue);
        } else {
            if (assetState.equals(AssetState.SUSPICIOUS)) {
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
        Map<List<String>, DtoSetter> fieldSetterMap = Map.ofEntries(
            entry(List.of(AssetDocumentFields.ACCOUNT_ID, AssetDocumentFields.LEGACY_ACCOUNT_ID),
                v -> {
                    dto.setAccountId(v.toString());
                    dto.setLegacyAccountId(v.toString());
                }),
            entry(List.of(AssetDocumentFields.LAST_SCAN_DATE,
                AssetDocumentFields.LEGACY_LAST_SCAN_DATE), v -> {
                dto.setLastScanDate(TimeHelper.parseDiscoveryDate(v.toString()));
                dto.setLegacyLastScanDate(TimeHelper.parseDiscoveryDate(v.toString()));
            }),
            entry(List.of(AssetDocumentFields.REGION), v -> dto.setRegion(v.toString())),
            entry(
                List.of(MapperFields.SOURCE_DISPLAY_NAME, MapperFields.LEGACY_SOURCE_DISPLAY_NAME),
                v -> {
                    dto.setSourceDisplayName(v.toString());
                    dto.setLegacySourceDisplayName(v.toString());
                }));

        fieldSetterMap.forEach((key, value) -> {
            var fieldValue = MapHelper.getFirstOrDefault(data, key, null);
            if (fieldValue != null) {
                value.set(fieldValue);
            }
        });

        dto.setIsEntity(true);
        dto.setEntityType(type);
        dto.setEntityTypeDisplayName(displayName);
        dto.setResourceName(data.getOrDefault(resourceNameField, idValue).toString());
        dto.setLoadDate(loadDate);
        dto.setIsLatest(true);

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

        var accountName = MapHelper.getFirstOrDefault(data,
            List.of(AssetDocumentFields.ACCOUNT_NAME, AssetDocumentFields.LEGACY_ACCOUNT_NAME,
                AssetDocumentFields.SUBSCRIPTION_NAME, AssetDocumentFields.PROJECT_NAME), null);
        if (accountName != null) {
            dto.setAccountName(accountName.toString());
            dto.setLegacyAccountName(accountName.toString());
        }

        if (data.containsKey(AssetDocumentFields.SUBSCRIPTION)) {
            dto.setAccountId(data.get(AssetDocumentFields.SUBSCRIPTION).toString());
            dto.setLegacyAccountId(data.get(AssetDocumentFields.SUBSCRIPTION).toString());
        } else if (data.containsKey(AssetDocumentFields.PROJECT_ID)) {
            dto.setAccountId(data.get(AssetDocumentFields.PROJECT_ID).toString());
            dto.setLegacyAccountId(data.get(AssetDocumentFields.PROJECT_ID).toString());
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
            var opinions = new HashMap<>(dto.getOpinions());
            var sourceOpinions = opinions.get(opinionSource);
            if (sourceOpinions != null && sourceOpinions.containsKey(opinionService)) {
                sourceOpinions = new HashMap<>(sourceOpinions);
                sourceOpinions.remove(opinionService);
                if (sourceOpinions.isEmpty()) {
                    opinions.remove(opinionSource);
                } else {
                    opinions.put(opinionSource, sourceOpinions);
                }
                dto.setOpinions(opinions);
            }

            return dto.getOpinions().isEmpty();
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

    interface MapperFields {

        String LEGACY_ACCOUNT_ID = "accountid";
        String ACCOUNT_ID = "account_id";

        String RAW_DATA = "rawData";
        String REPORTING_SOURCE = "reporting_source";

        String SOURCE_DISPLAY_NAME = "source_display_name";
        String LEGACY_SOURCE_DISPLAY_NAME = "sourceDisplayName";
    }


    interface DtoSetter {

        void set(Object value);
    }
}
