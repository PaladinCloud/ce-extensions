package com.paladincloud.assetsender;

import com.paladincloud.common.AssetStateStartEvent;
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
    // These optional arguments are provided for opinions; in that case, the reporting source
    // will differ from the data source and the opinion service will be used to store the opinion.
    private static final String REPORTING_SOURCE = "reporting_source";
    private static final String REPORTING_SOURCE_SERVICE = "reporting_source_service";
    private static final String REPORTING_SOURCE_SERVICE_DISPLAY_NAME = "reporting_source_service_display_name";

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

        LOGGER.info(
            "Processing assets; bucket={} dataSource={} path={} tenant={}",
            ConfigService.get(ConfigConstants.S3.BUCKET_NAME), dataSource, params.get(S3_PATH),
            tenantName);
        ConfigService.setProperties("batch.",
            Collections.singletonMap("s3.data", params.get(S3_PATH)));

        assetTypes.reset();

        var reportingSource = params.get(REPORTING_SOURCE);
        // dataSource is the underlying source of the data (gcp, aws, azure) while reporting source
        // is only set if it's different. It's different for secondary sources reporting data
        // (qualys, rapid7); in addition, reporting service is also set only if the data is from
        // a secondary source.
        var isOpinion = reportingSource != null && !dataSource.equalsIgnoreCase(reportingSource);

        if (!isOpinion) {
            assetTypes.setupIndexAndTypes(dataSource);
        }
        var processedAssetTypes = assets.process(dataSource, params.get(S3_PATH), isOpinion,
            reportingSource,
            params.get(REPORTING_SOURCE_SERVICE),
            params.get(REPORTING_SOURCE_SERVICE_DISPLAY_NAME));

        if (!isOpinion) {
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
        }

        // Have the asset state service update the found asset types
        var assetStateEvent = new AssetStateStartEvent(tenantId, dataSource,
            processedAssetTypes.stream().sorted().toArray(String[]::new),
            false).toCommandLine();

        if ("true".equalsIgnoreCase(ConfigService.get(ConfigConstants.Dev.OMIT_DONE_EVENT))) {
            LOGGER.warn("Omitting asset state event: {}",
                assetStateEvent);
        } else {
            sqsHelper.sendString(ConfigService.get(ConfigConstants.SQS.ASSET_STATE_START_SQS_URL),
                assetStateEvent, UUID.randomUUID().toString());
        }
    }

    @Override
    protected List<String> getRequiredFields() {
        return List.of(DATA_SOURCE, S3_PATH);
    }
}
