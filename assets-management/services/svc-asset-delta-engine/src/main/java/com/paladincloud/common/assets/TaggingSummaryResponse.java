package com.paladincloud.common.assets;

import java.util.List;

public record TaggingSummaryResponse(
        TaggingSummaryData data
) {

    public record TaggingSummaryData(
            String ag,
            int totalAssets,
            double overallCompliancePercentage,
            int overallTaggedCount,
            int overallAssetCount,
            String description,
            List<AssetGroupEntry> assetgroups
    ) {}

    public record AssetGroupEntry(
            String ag,
            Stats stats
    ) {}

    public record Stats(
            int totalAssets,
            double overallCompliancePercentage,
            int overallTaggedCount,
            int overallAssetCount,
            String description,
            List<AssetTypeDetail> assetTypes
    ) {}

    public record AssetTypeDetail(
            String targetType,
            String displayName,
            int assetCount,
            int taggedCount,
            int untaggedCount,
            double compliancePercentage,
            List<TagDetail> tagDetails
    ) {}

    public record TagDetail(
            String tagName,
            int count,
            double tagCompliancePercentage
    ) {}
}