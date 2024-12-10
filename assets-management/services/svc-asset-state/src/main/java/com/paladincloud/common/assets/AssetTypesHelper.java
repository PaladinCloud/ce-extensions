package com.paladincloud.common.assets;

import com.paladincloud.common.aws.DatabaseHelper;
import com.paladincloud.common.errors.JobException;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AssetTypesHelper {

    @Inject
    public AssetTypesHelper() {
    }

    public boolean isTypeManaged(String dataSource, String assetType) {
        var dbHelper = new DatabaseHelper();
        var rows = dbHelper.executeQuery(
            String.format("SELECT targetType,count(*) FROM pacmandata.cf_PolicyTable WHERE assetGroup = '%s' AND targetType= '%s' AND status = 'ENABLED'", dataSource, assetType));
        if (rows.size() == 1) {
            var firstRow = rows.getFirst();
            var count = Integer.parseInt(firstRow.get("count(*)"));
            return count > 1;
        } else {
            throw new JobException(String.format("1 row expected, %d returned for %s and %s", rows.size(), dataSource, assetType));
        }
    }
}
