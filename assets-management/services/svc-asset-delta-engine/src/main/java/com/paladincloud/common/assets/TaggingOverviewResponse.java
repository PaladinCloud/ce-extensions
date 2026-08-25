package com.paladincloud.common.assets;

import java.util.List;

public record TaggingOverviewResponse(
        TaggingOverviewData data
) {
    public record TaggingOverviewData(
            String ag,
            double overallCompliancePercentage,
            int overallTaggedCount,
            int overallAssetCount,
            double lastWeekCompliancePercentage,
            double overallDelta,
            int lastWeekTaggedCount,
            int lastWeekAssetCount,
            List<AssetTypeDetail> assetTypes
    ) {}

    public record AssetTypeDetail(
            String targetType,
            String displayName,
            int assetCount,
            int taggedCount,
            int untaggedCount,
            double compliancePercentage,
            double lastWeekCompliancePercentage,
            double delta,
            int lastWeekTaggedCount,
            int lastWeekAssetCount
    ) {}
}
