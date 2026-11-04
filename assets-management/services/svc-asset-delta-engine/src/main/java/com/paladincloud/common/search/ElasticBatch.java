package com.paladincloud.common.search;

import com.paladincloud.common.assets.AssetRepository;
import com.paladincloud.common.errors.JobException;
import com.paladincloud.common.util.MapHelper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.ResponseException;

public class ElasticBatch implements AssetRepository.Batch {

    private static final Logger LOGGER = LogManager.getLogger(ElasticBatch.class);
    private static final int DEFAULT_BATCH_SIZE = 5000;
    private static final int MAX_THROTTLE_RETRIES = 5;
    private final List<BatchItem> batchItems = new ArrayList<>();
    private final ElasticSearchHelper elasticSearch;
    private static final int DEFAULT_MAX_BATCH_BYTES = 5 * 1024 * 1024;
    private final int batchSize;
    private final int maxBatchBytes;
    private long batchBytes;

    @Inject
    public ElasticBatch(ElasticSearchHelper elasticSearch) {
        this(elasticSearch, DEFAULT_BATCH_SIZE, DEFAULT_MAX_BATCH_BYTES);
    }

    public ElasticBatch(ElasticSearchHelper elasticSearch, int batchSize, int maxBatchBytes) {
        if (batchSize <= 0 || maxBatchBytes <= 0) {
            throw new IllegalArgumentException("Batch count and byte limits must be positive");
        }
        this.elasticSearch = elasticSearch;
        this.batchSize = batchSize;
        this.maxBatchBytes = maxBatchBytes;
    }

    public void add(BatchItem batchData) throws IOException {
        // Count both NDJSON lines and their newlines in the HTTP payload's encoding.
        long itemBytes = batchData.actionMetaData.getBytes(StandardCharsets.UTF_8).length + 1L;
        if (batchData.document != null) {
            itemBytes += batchData.document.getBytes(StandardCharsets.UTF_8).length + 1L;
        }
        if (itemBytes > maxBatchBytes) {
            throw new JobException(STR."Bulk item is \{itemBytes} bytes, exceeding the \{maxBatchBytes}-byte batch limit; a single document cannot be split across requests");
        }
        if (batchBytes + itemBytes > maxBatchBytes) {
            push();
        }
        batchItems.add(batchData);
        batchBytes += itemBytes;
        checkForPush();
    }

    public void add(List<BatchItem> batchData) throws IOException {
        for (var item : batchData) {
            add(item);
        }
    }

    public void cancel() {
        LOGGER.info("Canceling batch with {} items", batchItems.size());
        batchItems.clear();
        batchBytes = 0;
    }

    public void flush() throws IOException {
        push();
    }

    @Override
    public void close() throws Exception {
        push();
    }

    private void checkForPush() throws IOException {
        if (batchItems.size() >= batchSize) {
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

        var response = sendWithBackoff(payload.toString());
        if (response.errors) {
            var failedItems = response.items.stream()
                .filter(i -> i.get("index") != null && ((Map<String, Object>)i.get("index")).get("error") != null)
                .toList();
            var niceItems = failedItems.stream().map(MapHelper::toJsonString).toList();

            LOGGER.error("ElasticBulkResponse failed with {} errors: {}", niceItems.size(), niceItems);
            throw new JobException("bulk insert failed");
        }
        batchItems.clear();
        batchBytes = 0;
    }

    private ElasticBulkResponse sendWithBackoff(String payload) throws IOException {
        for (int retry = 0; ; retry++) {
            try {
                return elasticSearch.invokeCheckAndConvert(ElasticBulkResponse.class,
                    ElasticSearchHelper.HttpMethod.POST, "/_bulk", payload);
            } catch (ResponseException e) {
                if (e.getResponse().getStatusLine().getStatusCode() != 429
                    || retry >= MAX_THROTTLE_RETRIES) {
                    throw e;
                }
                // Equal jitter avoids immediate retries and synchronized retry bursts.
                long ceilingMillis = 1000L << retry;
                long delayMillis = ThreadLocalRandom.current().nextLong(
                    ceilingMillis / 2, ceilingMillis + 1);
                LOGGER.warn("Bulk upload throttled (HTTP 429); retry {}/{} in {} ms",
                    retry + 1, MAX_THROTTLE_RETRIES, delayMillis);
                try {
                    waitBeforeRetry(delayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting to retry throttled bulk upload", interrupted);
                }
            }
        }
    }

    protected void waitBeforeRetry(long delayMillis) throws InterruptedException {
        Thread.sleep(delayMillis);
    }

    public static class BatchItem {

        public String actionMetaData;
        public String document;

        private BatchItem(String actionMetaData, String document) {
            this.actionMetaData = actionMetaData;
            this.document = document;
        }

        static public BatchItem deleteEntry(String indexName, String docId) {
            var actionInfo = STR."""
                { "delete": { "_index": "\{indexName}", "_id": "\{docId}" } }
                """.trim();
            return new BatchItem(actionInfo, null);
        }

        static public BatchItem documentEntry(String indexName, String docId,
            Map<String, ?> document) {
            return documentEntry(indexName, docId, MapHelper.toJsonString(document));
        }

        static public BatchItem documentEntry(String indexName, String docId, String document) {
            var actionInfo = STR."""
                { "index": { "_index": "\{indexName}", "_id": "\{docId}" } }
                """.trim();
            return new BatchItem(actionInfo, document);
        }

        static public BatchItem routingEntry(String indexName, String routingInfo,
            Map<String, ?> document) {
            return routingEntry(indexName, routingInfo, MapHelper.toJsonString(document));
        }

        static public BatchItem routingEntry(String indexName, String routingInfo,
            String document) {
            var actionInfo = STR."""
                { "index": { "_index": "\{indexName}", "routing": "\{routingInfo}" } }
                """.trim();
            return new BatchItem(actionInfo, document);
        }
    }
}
