package com.paladincloud.common;

/**
 * These are the fields in the ElasticSearch Asset documents
 */
public interface AssetDocumentFields {

    String LEGACY_NAME = "name";
    String ASSET_ID_DISPLAY_NAME = "assetIdDisplayName";

    String ASSET_STATE = "_assetState";

    String LEGACY_TARGET_TYPE_DISPLAY_NAME = "targetTypeDisplayName";
    String LEGACY_ENTITY_TYPE_DISPLAY_NAME = "targettypedisplayname";
    String LEGACY_SOURCE_DISPLAY_NAME = "sourceDisplayName";
    String SOURCE_DISPLAY_NAME = "source_display_name";
    String PRIMARY_PROVIDER = "primaryProvider";
    String OPINIONS = "opinions";

    String DOC_TYPE = "_docType";
    String LEGACY_DOC_TYPE = "docType";

    String LAST_SCAN_DATE = "_lastScanDate";
    String LEGACY_LAST_SCAN_DATE = "discoverydate";

    String FIRST_DISCOVERY_DATE = "_firstDiscoveryDate";
    String LEGACY_FIRST_DISCOVERY_DATE = "firstdiscoveredon";

    String LOAD_DATE = "_loadDate";
    String LEGACY_LOAD_DATE = "_loaddate";

    String ACCOUNT_ID = "account_id";
    String LEGACY_ACCOUNT_ID = "accountid";

    String ACCOUNT_NAME = "account_name";
    String LEGACY_ACCOUNT_NAME = "accountname";

    String PROJECT_ID = "projectId";
    String PROJECT_NAME = "projectName";
    String SUBSCRIPTION = "subscription";
    String SUBSCRIPTION_NAME = "subscriptionName";

    String SOURCE = "source";
    String LEGACY_SOURCE = "_cloudType";

    String REGION = "region";

    String DOC_ID = "_docId";
    String LEGACY_DOC_ID = "_docid";

    String IS_ENTITY = "_isEntity";
    String LEGACY_IS_ENTITY = "_entity";

    String ENTITY_TYPE = "_entityType";
    String LEGACY_ENTITY_TYPE = "_entitytype";

    String ENTITY_TYPE_DISPLAY_NAME = "_entityTypeDisplayName";
    String RELATIONS = "_relations";

    String IS_ACTIVE = "_isActive";

    String IS_LATEST = "_isLatest";
    String LEGACY_IS_LATEST = "latest";

    String RESOURCE_GROUP_NAME = "resourceGroupName";

    String RESOURCE_ID = "resource_id";
    String LEGACY_RESOURCE_ID = "_resourceid";

    String RESOURCE_NAME = "resource_name";
    String LEGACY_RESOURCE_NAME = "_resourcename";

    String TAGS = "tags";

    static String asKeyword(String field) {
        return STR."\{field}.keyword";
    }

    static String asTag(String field) {
        return STR."\{TAGS}.\{field}";
    }

}
