package com.paladincloud.common.aws;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class OpenSearchQueryResponse {

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
            public Map<String, Object> source;
        }
    }
}
