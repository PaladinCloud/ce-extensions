package com.paladincloud.common;

/**
 * These are the fields in the ElasticSearch Asset documents
 */
public interface AssetDocumentFields {

    String CSPM_SOURCE = "_cspm_source";
    String REPORTING_SOURCE = "_reporting_source";
    String NAME = "name";
    String ASSET_ID_DISPLAY_NAME = "assetIdDisplayName";
    /**
     * This is the legacy field for the target type display name; the field name is
     * all lowercase and is being replaced with `targetTypeDisplayName`. Until the
     * transition is complete, both fields will exist simultaneously.
     */
    String LEGACY_TARGET_TYPE_DISPLAY_NAME = "targettypedisplayname";
    String TARGET_TYPE_DISPLAY_NAME = "targetTypeDisplayName";
    String SOURCE_DISPLAY_NAME = "sourceDisplayName";
    String PRIMARY_PROVIDER = "primaryProvider";

    String DOC_TYPE = "docType";

    String DISCOVERY_DATE = "discoverydate";
    String FIRST_DISCOVERED = "firstdiscoveredon";
    String LOAD_DATE = "_loaddate";

    String ACCOUNT_ID = "accountid";
    String ACCOUNT_NAME = "accountname";
    String PROJECT_ID = "projectId";
    String PROJECT_NAME = "projectName";
    String SUBSCRIPTION = "subscription";
    String SUBSCRIPTION_NAME = "subscriptionName";

    String CLOUD_TYPE = "_cloudType";
    String DOC_ID = "_docid";
    String ENTITY = "_entity";
    String ENTITY_TYPE = "_entitytype";
    String RELATIONS = "_relations";

    String LATEST = "latest";

    String RESOURCE_GROUP_NAME = "resourceGroupName";
    String RESOURCE_ID = "_resourceid";
    String RESOURCE_NAME = "_resourcename";

    String TAGS = "tags";

    static String asKeyword(String field) {
        return STR."\{field}.keyword";
    }

    static String asTag(String field) {
        return STR."\{TAGS}.\{field}";
    }

}
