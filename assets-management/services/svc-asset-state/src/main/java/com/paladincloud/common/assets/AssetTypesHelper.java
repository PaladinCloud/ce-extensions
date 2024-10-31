package com.paladincloud.common.assets;

import com.paladincloud.common.aws.DatabaseHelper;
import com.paladincloud.common.errors.JobException;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AssetTypesHelper {

    @Inject
    AssetTypesHelper() {
    }

    public boolean isTypeManaged(String dataSource, String assetType) {
        var dbHelper = new DatabaseHelper();
        var rows = dbHelper.executeQuery(
            STR."SELECT targetType,count(*) FROM pacmandata.cf_PolicyTable WHERE assetGroup = '\{dataSource}' AND targetType= '\{assetType}' AND status = 'ENABLED'");
        if (rows.size() == 1) {
            var firstRow = rows.getFirst();
            var count = Integer.parseInt(firstRow.get("count(*)"));
            return count > 1;
        } else {
            throw new JobException(STR."1 row expected, \{rows.size()} returned for \{dataSource} and \{assetType}");
        }
    }
}
