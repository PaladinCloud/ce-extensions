package com.paladincloud.common.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paladincloud.common.assets.AssetDTO;
import java.util.List;

public class ElasticQueryAssetResponse {

    @JsonProperty("_scroll_id")
    public String scrollId;
    public Hits hits;

    public static class Hits {

        public List<HitsDoc> hits;

        public static class HitsDoc {

            @JsonProperty("_index")
            public String index;
            @JsonProperty("_id")
            public String id;
            @JsonProperty("_source")
            public AssetDTO source;
        }
    }

}
