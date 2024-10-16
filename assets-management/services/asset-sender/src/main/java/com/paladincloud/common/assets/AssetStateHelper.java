package com.paladincloud.common.assets;

import com.paladincloud.common.aws.DatabaseHelper;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AssetStateHelper {

    private final DatabaseHelper databaseHelper;

    private final Map<String, Map<String, Integer>> sourceTypeMap = new HashMap<>();

    @Inject
    public AssetStateHelper(DatabaseHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
    }

    public AssetState get(String dataSource, String assetType) {
        var typeCountMap = getSourceTypeMap(dataSource);
        if (typeCountMap.getOrDefault(assetType, 0).equals(0)) {
            return AssetState.UNMANAGED;
        }
        return AssetState.MANAGED;
    }

    private Map<String, Integer> getSourceTypeMap(String dataSource) {
        if (!sourceTypeMap.containsKey(dataSource)) {
            var typeCountMap = new HashMap<String, Integer>();
            databaseHelper.executeQuery(
                    STR."SELECT targetType,count(*) FROM pacmandata.cf_PolicyTable WHERE assetGroup = '\{dataSource}' AND status = 'ENABLED' GROUP BY targetType")
                .forEach(row -> {
                    var type = row.get("targetType");
                    var count = row.get("count(*)");
                    typeCountMap.put(type, Integer.parseInt(count));
                });
            sourceTypeMap.put(dataSource, typeCountMap);
        }
        return sourceTypeMap.get(dataSource);
    }
}
