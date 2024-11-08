package com.paladincloud.common.aws;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OpenSearchErrorCause {
    public String type;
    public String reason;
    @JsonProperty("caused_by")
    public CausedBy causedBy;

    @Override
    public String toString() {
        return String.format("type=%s reason=%s causedBy='%s'", type, reason,
            causedBy);
    }

    public static final class CausedBy {

        public String type;
        public String reason;

        @Override
        public String toString() {
            return String.format("type=%s reason=%s", type, reason);
        }
    }


}
