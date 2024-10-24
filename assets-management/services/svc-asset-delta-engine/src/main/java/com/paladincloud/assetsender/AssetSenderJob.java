package com.paladincloud.assetsender;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paladincloud.common.ProcessingDoneMessage;
import com.paladincloud.common.assets.AssetCounts;
import com.paladincloud.common.assets.AssetGroupStatsCollector;
import com.paladincloud.common.assets.Assets;
import com.paladincloud.common.assets.DataSourceHelper;
import com.paladincloud.common.aws.SQSHelper;
import com.paladincloud.common.config.AssetTypes;
import com.paladincloud.common.config.ConfigConstants;
import com.paladincloud.common.config.ConfigConstants.Dev;
import com.paladincloud.common.config.ConfigService;
import com.paladincloud.common.errors.JobException;
import com.paladincloud.common.jobs.JobExecutor;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AssetSenderJob extends JobExecutor {

    public static final String DATA_SOURCE = "data_source";
    public static final String S3_PATH = "s3_path";
    private static final Logger LOGGER = LogManager.getLogger(AssetSenderJob.class);

    private final AssetTypes assetTypes;
    private final Assets assets;
    private final SQSHelper sqsHelper;
    private final DataSourceHelper dataSourceHelper;
    private final AssetGroupStatsCollector assetGroupStatsCollector;
    private final AssetCounts assetCounts;

    @Inject
    AssetSenderJob(AssetTypes assetTypes, Assets assets, SQSHelper sqsHelper,
        DataSourceHelper dataSourceHelper, AssetGroupStatsCollector assetGroupStatsCollector,
        AssetCounts assetCounts) {
        this.assetTypes = assetTypes;
        this.assets = assets;
        this.sqsHelper = sqsHelper;
        this.dataSourceHelper = dataSourceHelper;
        this.assetGroupStatsCollector = assetGroupStatsCollector;
        this.assetCounts = assetCounts;
    }


    @Override
    protected void execute() {
        var dataSource = params.get(DATA_SOURCE);

        LOGGER.info("Processing assets; bucket={} datasource={} path={} tenant={}",
            ConfigService.get(ConfigConstants.S3.BUCKET_NAME), dataSource, params.get(S3_PATH),
            tenantName);
        ConfigService.setProperties("batch.",
            Collections.singletonMap("s3.data", params.get(S3_PATH)));

        assetTypes.setupIndexAndTypes(dataSource);
        assets.process(dataSource);

        if ("true".equalsIgnoreCase(ConfigService.get(Dev.SKIP_ASSET_COUNT))) {
            LOGGER.error("Skipping asset count");
        } else {
            try {
                var dataSourceInfo = dataSourceHelper.fetch(dataSource);
                assetGroupStatsCollector.collectStats(dataSourceInfo.assetGroups());
                assetCounts.populate(dataSource, dataSourceInfo.accountIds());
            } catch (Exception e) {
                throw new JobException("Error populating asset stats", e);
            }
        }

        // NOTE: enricher source will be set when handling secondary sources
        String enricherSource = null;
        var shipperDoneEvent = new ProcessingDoneMessage(STR."\{dataSource}-asset-shipper",
            dataSource, enricherSource,
            tenantId, tenantName);
        if ("true".equalsIgnoreCase(ConfigService.get(ConfigConstants.Dev.OMIT_DONE_EVENT))) {
            try {
                LOGGER.warn("Omitting done event: {}",
                    new ObjectMapper().writeValueAsString(shipperDoneEvent));
            } catch (JsonProcessingException e) {
                throw new JobException("Failed serializing event", e);
            }
        } else {
            sqsHelper.sendMessage(ConfigService.get(ConfigConstants.SQS.ASSET_SHIPPER_DONE_SQS_URL),
                shipperDoneEvent, UUID.randomUUID().toString());
        }
    }

    @Override
    protected List<String> getRequiredFields() {
        return List.of(DATA_SOURCE, S3_PATH);
    }
}
