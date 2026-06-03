package com.paladincloud.datamapper.model;

import lombok.Getter;

@Getter
public class AssetMappingDoneEvent {
    private final String tenantId;
    private final String tenantName;
    private final String dataSource;
    private final String s3Path;
    private final String reportingSource;
    private final String reportingService;
    private final String reportingServiceDisplayName;
    private final String sourceDisplayName;


    public AssetMappingDoneEvent(String tenantId,String tenantName, String dataSource, String s3Path,
                                 String reportingSource, String reportingService, String reportingServiceDisplayName, String sourceDisplayName) {
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.dataSource = dataSource;
        this.s3Path = s3Path;
        this.reportingSource = reportingSource;
        this.reportingService = reportingService;
        this.reportingServiceDisplayName = reportingServiceDisplayName;
        this.sourceDisplayName = sourceDisplayName;
    }

    public String toCommandLine() {
        return "--tenant_id=" +tenantId + " --tenant_name=" +tenantName +" --data_source=" + dataSource + " --s3_path=" + s3Path
                +" --reporting_source=" + reportingSource + " --reporting_source_service=" + reportingService
                +" \"--reporting_source_service_display_name=" + reportingServiceDisplayName + "\" --reporting_source_display_name=" + sourceDisplayName;
    }
}
