/*******************************************************************************
 * Copyright 2023 Paladin Cloud, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package com.paladincloud.datamapper.commons;

public class MapperConstants {
    private MapperConstants() {
        throw new IllegalStateException("MapperConstants is a Utility class");
    }

    public static final String JSON_ROOT_PATH = "$.";
    public static final String FILE_NAME_DELIMITER = "_";
    public static final String FIELD_NAME_TOP_LEVEL_FIELDS = "top_level_fields";
    public static final String FIELD_NAME_CONDITIONAL_TOP_LEVEL_FIELDS = "conditional_top_level_fields";
    public static final String FIELD_NAME_PAYLOAD = "payload";
    public static final String FIELD_NAME_ADDITIONAL_FIELDS = "additional_fields";
    public static final String REPLACEMENT_KEY = "replacement_fields";
    public static final String MAPPER_FILE_EXTENSION = ".json";
    public static final String MAPPER_RULES_KB_FILENAME_NOTATION = "rules_kb";
    public static final String DEFAULT_OPEN_SEARCH_DATE_FORMAT = "yyyy-MM-dd HH:mm:00Z";
    // This is a comma-separated list of plugins that are directed to the delta engine
    public static final String ENABLED_DELTA_ENGINE_PLUGINS = "ENABLED_DELTA_ENGINE_PLUGINS";
    public static final String CQ_OUTPUT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSSSSS";
    public static final String MAPPER_BUCKET_NAME = "MAPPER_BUCKET_NAME";
    public static final String MAPPER_FOLDER_NAME = "MAPPER_FOLDER_NAME";
    public static final String ASSET_MAPPING_DONE_QUEUE_URL = "ASSET_MAPPING_DONE_QUEUE_URL";
    public static final String MAPPING_DONE_QUEUE_URL = "MAPPING_DONE_QUEUE_URL";
    public static final String DESTINATION_BUCKET = "DESTINATION_BUCKET";
    public static final String DESTINATION_FOLDER = "DESTINATION_FOLDER";
    public static final String FIELD_NAME_FILTER_PATTERN = "pattern";
    public static final String FIELD_NAME_FILTER_FIELD = "field";
    public static final String FIELD_NAME_FILTER_FIELDS = "fields";
    public static final String CONFIG_URL = "CONFIG_URL";
    public static final String ENV = "ENV";
    public static final String CONFIG_CREDS = "CONFIG_CREDS";
    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String FIELD_NAME_METADATA = "metadata";
    public static final String FUNCTION_ARGUMENTS_DELIMITER = ",";
    public static final String MULTI_SOURCE_FIELD_DELIMITER = ";";
    public static final String FIELD_STARTS_WITH_HASH = "#[";
    public static final String BASE_ASSET_MAPPER_FILE_NAME = "basic_asset_mapper.json";
    public static final String COMMON_FIELDS = "common_fields";
    public static final String TARGET_TYPE_COMMON_FIELDS = "target_type_common_fields";
    public static final String RAW_DATA_FILE_NAME = "rawDataFile";
    public static final String DOC_ID = "_docId";
    public static final String INTERNAL_DNS_URL = "INTERNAL_DNS_URL";
}
