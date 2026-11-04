package com.paladincloud.commons.search;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.paladincloud.common.search.ElasticBatch;
import com.paladincloud.common.search.ElasticBatch.BatchItem;
import com.paladincloud.common.search.ElasticResponse;
import com.paladincloud.common.search.ElasticSearchHelper;
import com.paladincloud.common.search.ElasticSearchHelper.HttpMethod;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElasticBatchTests {

    @Spy
    ElasticSearchHelper spyElasticSearch;
    @Mock
    ElasticSearchHelper mockedElasticSearch;

    // KVT TODO: I'm still in the process of working through this test.
    /*
    @Test
    void batchFormsQueryCorrectly() throws Exception {
        var expectedPayload = """
            { "index": { "_index": "testing", "_id": "id-1" } }
            { "some": "data" }
            """;

        var elasticResponse = new ElasticResponse(200, "OK", """
            { "errors": 0, "items": [] }
            """);
        // If this test fails due to 'Strict stubbing argument mismatch', it's due to
        // the generated payload differing from the expectedPayload, which needs to
        // be analyzed.
        when(mockedElasticSearch.invokeAndCheck(HttpMethod.POST, "/_bulk",
            expectedPayload)).thenReturn(elasticResponse);

        assertDoesNotThrow(() -> {
            try (var batch = new ElasticBatch(mockedElasticSearch)) {
                batch.add(BatchItem.documentEntry("testing", "id-1", """
                    { "some": "data" }
                    """.trim()));
            }
        });
    }
     */

    /**
     * This confirms that invokeAndCheck properly handles a bad request
     */
    @Test
    void failedInsertThrowsException() throws Exception {
        var payload = """
            { "index": { "_index": "testing", "_id": "id-1" } }
            { "some": "data" }
            """;
        doReturn(getBadParameterResponse()).when(spyElasticSearch)
            .invoke(HttpMethod.POST, "/_bulk", payload);

        assertThrows(IOException.class, () -> {
            try (var batch = new ElasticBatch(spyElasticSearch)) {
                batch.add(BatchItem.documentEntry("testing", "id-1", """
                    { "some": "data" }
                    """.trim()));
            }
        });
    }

    private java.util.List<String> capturePayloads() throws IOException {
        var payloads = new java.util.ArrayList<String>();
        org.mockito.Mockito.doAnswer(invocation -> {
            payloads.add(invocation.getArgument(3));
            return new com.paladincloud.common.search.ElasticBulkResponse();
        }).when(mockedElasticSearch).invokeCheckAndConvert(
            org.mockito.ArgumentMatchers.eq(com.paladincloud.common.search.ElasticBulkResponse.class),
            org.mockito.ArgumentMatchers.eq(HttpMethod.POST),
            org.mockito.ArgumentMatchers.eq("/_bulk"),
            org.mockito.ArgumentMatchers.anyString());
        return payloads;
    }

    private String payload(BatchItem item) {
        return item.actionMetaData + "\n" + (item.document == null ? "" : item.document + "\n");
    }

    @Test
    void listIsSplitByUtf8BytesKeepingActionsAndDocumentsTogether() throws Exception {
        var item = BatchItem.documentEntry("testing", "id-1", "{\"name\":\"東京😀\"}");
        var itemPayload = payload(item);
        int limit = itemPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        var payloads = capturePayloads();
        try (var batch = new ElasticBatch(mockedElasticSearch, 5000, limit)) {
            batch.add(java.util.List.of(item, item, item));
        }
        org.junit.jupiter.api.Assertions.assertEquals(
            java.util.List.of(itemPayload, itemPayload, itemPayload), payloads);
        payloads.forEach(body -> org.junit.jupiter.api.Assertions.assertTrue(
            body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= limit));
    }

    @Test
    void listIsSplitByCountAndCloseFlushesRemainderOnce() throws Exception {
        var item = BatchItem.documentEntry("testing", "id-1", "{}");
        var payloads = capturePayloads();
        try (var batch = new ElasticBatch(mockedElasticSearch, 2, 10000)) {
            batch.add(java.util.List.of(item, item, item, item, item));
            batch.flush();
        }
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(
            payload(item).repeat(2), payload(item).repeat(2), payload(item)), payloads);
    }

    @Test
    void deleteActionsRespectByteLimitAndCancelResetsSize() throws Exception {
        var item = BatchItem.deleteEntry("testing", "id-1");
        int limit = payload(item).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        var payloads = capturePayloads();
        try (var batch = new ElasticBatch(mockedElasticSearch, 5000, limit)) {
            batch.add(item);
            batch.cancel();
            batch.add(item);
            batch.add(item);
        }
        org.junit.jupiter.api.Assertions.assertEquals(
            java.util.List.of(payload(item), payload(item)), payloads);
    }

    @Test
    void oversizedSingleItemFailsBeforeSending() throws Exception {
        var item = BatchItem.documentEntry("testing", "id-1", "{}");
        int limit = payload(item).getBytes(java.nio.charset.StandardCharsets.UTF_8).length - 1;
        try (var batch = new ElasticBatch(mockedElasticSearch, 5000, limit)) {
            var error = assertThrows(com.paladincloud.common.errors.JobException.class,
                () -> batch.add(item));
            org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("cannot be split"));
        }
        org.mockito.Mockito.verifyNoInteractions(mockedElasticSearch);
    }

    @Test
    void failedFlushRetainsPendingItemsForRetry() throws Exception {
        var item = BatchItem.documentEntry("testing", "id-1", "{}");
        when(mockedElasticSearch.invokeCheckAndConvert(
            com.paladincloud.common.search.ElasticBulkResponse.class,
            HttpMethod.POST, "/_bulk", payload(item)))
            .thenThrow(new IOException("upload failed"))
            .thenReturn(new com.paladincloud.common.search.ElasticBulkResponse());
        try (var batch = new ElasticBatch(mockedElasticSearch, 5000, 10000)) {
            batch.add(item);
            assertThrows(IOException.class, batch::flush);
            batch.flush();
        }
        org.mockito.Mockito.verify(mockedElasticSearch, org.mockito.Mockito.times(2))
            .invokeCheckAndConvert(com.paladincloud.common.search.ElasticBulkResponse.class,
                HttpMethod.POST, "/_bulk", payload(item));
    }

    private org.elasticsearch.client.ResponseException httpError(int status) throws IOException {
        var response = org.mockito.Mockito.mock(org.elasticsearch.client.Response.class);
        when(response.getStatusLine()).thenReturn(new org.apache.http.message.BasicStatusLine(
            org.apache.http.HttpVersion.HTTP_1_1, status, "test error"));
        when(response.getRequestLine()).thenReturn(new org.apache.http.message.BasicRequestLine(
            "POST", "/_bulk", org.apache.http.HttpVersion.HTTP_1_1));
        when(response.getHost()).thenReturn(new org.apache.http.HttpHost("localhost", 9200));
        return new org.elasticsearch.client.ResponseException(response);
    }

    private class NoWaitBatch extends ElasticBatch {
        final java.util.List<Long> delays = new java.util.ArrayList<>();
        boolean interrupt;

        NoWaitBatch() {
            super(mockedElasticSearch);
        }

        @Override
        protected void waitBeforeRetry(long delayMillis) throws InterruptedException {
            delays.add(delayMillis);
            if (interrupt) {
                throw new InterruptedException("test interruption");
            }
        }
    }

    @Test
    void throttledRequestRetriesSamePayloadThenClearsBatch() throws Exception {
        var item = BatchItem.documentEntry("testing", "id-1", "{}");
        var responseError = httpError(429);
        when(mockedElasticSearch.invokeCheckAndConvert(
            com.paladincloud.common.search.ElasticBulkResponse.class,
            HttpMethod.POST, "/_bulk", payload(item)))
            .thenThrow(responseError)
            .thenThrow(responseError)
            .thenReturn(new com.paladincloud.common.search.ElasticBulkResponse());
        try (var batch = new NoWaitBatch()) {
            batch.add(item);
            batch.flush();
            org.junit.jupiter.api.Assertions.assertEquals(2, batch.delays.size());
            org.junit.jupiter.api.Assertions.assertTrue(batch.delays.get(0) >= 500 && batch.delays.get(0) <= 1000);
            org.junit.jupiter.api.Assertions.assertTrue(batch.delays.get(1) >= 1000 && batch.delays.get(1) <= 2000);
        }
        org.mockito.Mockito.verify(mockedElasticSearch, org.mockito.Mockito.times(3))
            .invokeCheckAndConvert(com.paladincloud.common.search.ElasticBulkResponse.class,
                HttpMethod.POST, "/_bulk", payload(item));
    }

    @Test
    void persistentThrottleStopsAfterFiveRetriesAndRetainsPendingBatch() throws Exception {
        var item = BatchItem.documentEntry("testing", "id-1", "{}");
        var responseError = httpError(429);
        when(mockedElasticSearch.invokeCheckAndConvert(
            com.paladincloud.common.search.ElasticBulkResponse.class,
            HttpMethod.POST, "/_bulk", payload(item))).thenThrow(responseError);
        var batch = new NoWaitBatch();
        batch.add(item);
        assertThrows(org.elasticsearch.client.ResponseException.class, batch::flush);
        org.junit.jupiter.api.Assertions.assertEquals(5, batch.delays.size());
        org.mockito.Mockito.verify(mockedElasticSearch, org.mockito.Mockito.times(6))
            .invokeCheckAndConvert(com.paladincloud.common.search.ElasticBulkResponse.class,
                HttpMethod.POST, "/_bulk", payload(item));
        org.mockito.Mockito.doReturn(new com.paladincloud.common.search.ElasticBulkResponse())
            .when(mockedElasticSearch).invokeCheckAndConvert(
                com.paladincloud.common.search.ElasticBulkResponse.class,
                HttpMethod.POST, "/_bulk", payload(item));
        batch.flush();
        batch.close();
    }

    @Test
    void nonThrottleHttpFailureIsNotRetried() throws Exception {
        var item = BatchItem.documentEntry("testing", "id-1", "{}");
        var responseError = httpError(413);
        when(mockedElasticSearch.invokeCheckAndConvert(
            com.paladincloud.common.search.ElasticBulkResponse.class,
            HttpMethod.POST, "/_bulk", payload(item))).thenThrow(responseError);
        var batch = new NoWaitBatch();
        batch.add(item);
        assertThrows(org.elasticsearch.client.ResponseException.class, batch::flush);
        org.junit.jupiter.api.Assertions.assertTrue(batch.delays.isEmpty());
        batch.cancel();
        batch.close();
        org.mockito.Mockito.verify(mockedElasticSearch).invokeCheckAndConvert(
            com.paladincloud.common.search.ElasticBulkResponse.class,
            HttpMethod.POST, "/_bulk", payload(item));
    }

    @Test
    void interruptedBackoffStopsRetriesAndRestoresInterruptFlag() throws Exception {
        var item = BatchItem.documentEntry("testing", "id-1", "{}");
        var responseError = httpError(429);
        when(mockedElasticSearch.invokeCheckAndConvert(
            com.paladincloud.common.search.ElasticBulkResponse.class,
            HttpMethod.POST, "/_bulk", payload(item))).thenThrow(responseError);
        var batch = new NoWaitBatch();
        batch.interrupt = true;
        batch.add(item);
        try {
            var error = assertThrows(IOException.class, batch::flush);
            org.junit.jupiter.api.Assertions.assertInstanceOf(InterruptedException.class, error.getCause());
            org.junit.jupiter.api.Assertions.assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
            batch.cancel();
            batch.close();
        }
        org.mockito.Mockito.verify(mockedElasticSearch).invokeCheckAndConvert(
            com.paladincloud.common.search.ElasticBulkResponse.class,
            HttpMethod.POST, "/_bulk", payload(item));
    }

    private ElasticResponse getBadParameterResponse() {
        return new ElasticResponse(400, "Bad Request", null);
    }
}
