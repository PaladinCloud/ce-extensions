package com.paladincloud.common.config;

public interface ConfigConstants {

    interface Tenant {

        String TENANT_NAME = "config.tenant_name";
    }

    interface Dev {

        String INDEX_PREFIX = "param.index_prefix";
        String ASSET_TYPE_OVERRIDE = "param.asset_type_override";
        String OMIT_DONE_EVENT = "param.omit_done_event";
        String SKIP_ASSET_COUNT = "param.skip_asset_count";
    }

    interface Config {

        String TYPES_QUERY = "param.config-query";
        String TARGET_TYPE_INCLUDE = "param.target-type-include";
        String TARGET_TYPE_EXCLUDE = "param.target-type-exclude";
    }

    interface Elastic {

        String HOST = "batch.elastic-search.host";
        String PORT = "batch.elastic-search.port";
    }

    interface PaladinCloud {

        String API_AUTH_CREDENTIALS = "application.apiauthinfo";
        String AUTH_API_URL = "environment.AUTH_API_URL";
        String BASE_PALADIN_CLOUD_API_URI = "config.base-paladincloud-api-url";
    }

    interface RDS {

        String DB_URL = "batch.spring.datasource.url";
        String USER = "batch.spring.datasource.username";
        String PWD = "batch.spring.datasource.password";

    }

    interface S3 {

        String BUCKET_NAME = "batch.s3";
        String DATA_PATH = "batch.s3.data";
    }

    interface SQS {

        String ASSET_SHIPPER_DONE_SQS_URL = "config.asset-shipper-done-sqs-url";
    }
}
