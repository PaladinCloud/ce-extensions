package com.paladincloud.common.aws;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paladincloud.assetstate.AssetFieldNames;
import com.paladincloud.assetstate.AssetState;
import com.paladincloud.common.aws.AssetStorageHelper.HttpMethod;
import com.paladincloud.common.aws.ElasticBatch.ElasticBulkResponse.Item;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ElasticBatch implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger(ElasticBatch.class);
    private static final int DEFAULT_BATCH_SIZE = 5000;
    private final List<BatchItem> batchItems = new ArrayList<>();
    private AssetStorageHelper storageHelper;

    public ElasticBatch(AssetStorageHelper storageHelper) {
        this.storageHelper = storageHelper;
    }

    public void add(BatchItem batchData) throws IOException {
        batchItems.add(batchData);
        checkForPush();
    }

    public void add(List<BatchItem> batchData) throws IOException {
        batchItems.addAll(batchData);
        checkForPush();
    }

    public void cancel() {
        LOGGER.info("Canceling batch with {} items", batchItems.size());
        batchItems.clear();
    }

    public void flush() throws IOException {
        push();
    }

    public void close() throws Exception {
        push();
    }

    private void checkForPush() throws IOException {
        if (batchItems.size() >= DEFAULT_BATCH_SIZE) {
            push();
        }
    }

    private void push() throws IOException {
        if (batchItems.isEmpty()) {
            return;
        }

        // A bulk request comprises two-line pairs; the first line indicates the action & index
        // and the second line is the document
        var payload = new StringBuilder(2048);
        for (var batchData : batchItems) {
            payload.append(batchData.actionMetaData);
            payload.append("\n");
            if (batchData.document != null) {
                payload.append(batchData.document);
                payload.append("\n");
            }
        }

        var rawBodyResponse = storageHelper.invokeAndCheck(HttpMethod.POST, "/_bulk",
            payload.toString());
        var batchResponse = storageHelper.classFromString(ElasticBulkResponse.class,
            rawBodyResponse.getBody());
        if (batchResponse.errors) {
            var failedItems = batchResponse.items.stream()
                .map(i -> i.get("update"))
                .filter(i -> i != null && i.error != null)
                .toList();
            var formatted = failedItems.stream().map(Item::toString).toList();
            LOGGER.error("Bulk update failed for {} of {} items: {}", failedItems.size(),
                batchItems.size(), formatted);
            throw new RuntimeException("bulk insert failed");
        }

        batchItems.clear();
    }

    public static class ElasticBulkResponse {

        public boolean errors;
        public List<Map<String, Item>> items;

        public static final class Item {

            @JsonProperty("_index")
            public String index;
            @JsonProperty("_id")
            public String id;
            public int status;
            public OpenSearchErrorCause error;

            @Override
            public String toString() {
                return String.format("index=%s id=%s status=%s error=%s", index, id, status, error);
            }

        }
    }

    @Getter
    public static class BatchItem {

        private final String actionMetaData;
        private final String document;

        private BatchItem(String actionMetaData, String document) {
            this.actionMetaData = actionMetaData;
            this.document = document;
        }

        public static BatchItem updateState(String indexName, String docId,
            AssetState state) {
            return update(indexName, docId, String.format("""
                    { "doc": { "%s": "%s" } }
                    """, AssetFieldNames.ASSET_STATE, state.getName())
                .trim());
        }

        public static BatchItem update(String indexName, String docId, String document) {
            var actionInfo = String.format("""
                { "update": { "_index": "%s", "_id": "%s" } }
                """, indexName, docId).trim();
            return new BatchItem(actionInfo, document);
        }
    }
}
