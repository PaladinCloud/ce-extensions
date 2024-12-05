package com.paladincloud.common.aws;

import java.util.List;

public class OpenSearchUpdateByQueryResponse {

    public Long total;
    public Long updated;
    public List<Failure> failures;

    public static final class Failure {
        public String id;
        public OpenSearchErrorCause error;
    }
}
