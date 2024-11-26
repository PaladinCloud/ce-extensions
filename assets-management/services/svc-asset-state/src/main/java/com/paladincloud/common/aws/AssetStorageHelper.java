package com.paladincloud.common.aws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paladincloud.assetstate.AssetFieldNames;
import com.paladincloud.assetstate.AssetState;
import com.paladincloud.assetstate.PartialAssetDTO;
import com.paladincloud.common.aws.ElasticBatch.BatchItem;
import com.paladincloud.common.config.ConfigConstants;
import com.paladincloud.common.config.Configuration;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;

@Singleton
public class AssetStorageHelper {

    private static final Logger LOGGER = LogManager.getLogger(AssetStorageHelper.class);
    private static final int MAX_RETURNED_RESULTS = 10000;

    private static final ObjectMapper objectMapper = new ObjectMapper().configure(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModule(new JavaTimeModule());
    private final List<String> idFields = List.of(AssetFieldNames.DOC_ID,
        AssetFieldNames.LEGACY_DOC_ID);
    private final List<String> neededFields = List.of(AssetFieldNames.DOC_ID,
        AssetFieldNames.LEGACY_DOC_ID, AssetFieldNames.ASSET_STATE, AssetFieldNames.PRIMARY_PROVIDER);
    private RestClient restClient;

    public Set<PartialAssetDTO> getOpinions(String dataSource, String type) {
        return fetchAll(
            getOpinionIndexName(dataSource, type),
            """
                {
                    "query": {
                        "match_all": {}
                    }
                }
                """.trim());
    }

    public Set<PartialAssetDTO> getPrimary(String dataSource, String type) {
        return fetchAll(
            getPrimaryIndexName(dataSource, type),
            """
                {
                  "query": {
                    "bool": {
                      "must": [
                        {
                          "term": {
                            "latest": {
                              "value": true
                            }
                          }
                        },
                        {
                          "term": {
                            "_entity": {
                              "value": "true"
                            }
                          }
                        }
                      ]
                    }
                  }
                }
                """.trim());
    }

    public void toggleState(String dataSource, String type, AssetState oldState, AssetState newState) {
        var payload = String.format(
            """
                {
                  "script": {
                    "source": "ctx._source._assetState='%s'",
                    "lang": "painless"
                  },
                  "query": {
                    "term": {
                      "_assetState.keyword": "%s"
                    }
                  }
                }
            """, newState.getName(), oldState.getName()).trim();

        var endPoint = getPrimaryIndexName(dataSource, type) + "/_update_by_query";
        try {
            var bodyResponse = invokeAndCheck(HttpMethod.POST, endPoint, payload);
            var updateResponse = classFromString(OpenSearchUpdateByQueryResponse.class, bodyResponse.getBody());
            if (updateResponse.failures != null && !updateResponse.failures.isEmpty()) {
                LOGGER.error("There were asset status update failures: {}", updateResponse.failures);
            }
            LOGGER.info("Toggled {} of {} {} {} from {} to {}",
                updateResponse.updated, updateResponse.total, dataSource, type, oldState.getName(), newState.getName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setStates(String dataSource, String type, Collection<PartialAssetDTO> assets) {
        if (assets.isEmpty()) {
            return;
        }

        try (var batch = new ElasticBatch(this)) {
            var indexName = getPrimaryIndexName(dataSource, type);
            assets.forEach(asset -> {
                try {
                    batch.add(
                        BatchItem.updateState(indexName, asset.getDocId(), asset.getAssetState()));
                } catch (IOException e) {
                    LOGGER.error("Failed batching item to update state", e);
                }
            });
        } catch (Exception ex) {
            LOGGER.error("Batch state update failed", ex);
        }
    }

    private Set<PartialAssetDTO> fetchAll(String indexName, String query) {
        var allFieldsFilter = neededFields.stream()
            .map(f -> String.format(",hits.hits._source.%s", f))
            .collect(
                Collectors.joining());
        var fieldFilter = String.format("_scroll_id%s", allFieldsFilter);
        var endPoint = String.format("%s/_search?scroll=1m&filter_path=%s&size=%d", indexName,
            fieldFilter, MAX_RETURNED_RESULTS);
        var allResults = new HashSet<PartialAssetDTO>();
        var fetchResults = new HashSet<PartialAssetDTO>();
        var scrollId = fetchAndScroll(endPoint, fetchResults, query);
        while (fetchResults.size() == MAX_RETURNED_RESULTS) {
            allResults.addAll(fetchResults);
            fetchResults.clear();
            endPoint = String.format("/_search/scroll?scroll=1m&scroll_id=%s", scrollId);
            scrollId = fetchAndScroll(endPoint, fetchResults, null);
        }
        allResults.addAll(fetchResults);
        return allResults;
    }

    private String fetchAndScroll(String endPoint, Set<PartialAssetDTO> results,
        String payload) {
        try {
            var response = classFromString(OpenSearchQueryResponse.class,
                invokeAndCheck(HttpMethod.GET, endPoint, payload).getBody());
            if (response.hits != null && response.hits.hits != null) {
                for (var hit : response.hits.hits) {
                    var idValues = idFields.stream().map(f -> hit.source.get(f)).filter(
                        Objects::nonNull).toList();
                    if (idValues.isEmpty()) {
                        LOGGER.error("error occurred in: Asset missing '{}' field(s)", idFields);
                    } else {
                        AssetState state = null;
                        var assetStateValue = hit.source.get(AssetFieldNames.ASSET_STATE);
                        if (assetStateValue != null) {
                            state = AssetState.valueOf(assetStateValue.toString().toUpperCase());
                        }
                        var asset = PartialAssetDTO.builder()
                            .docId(idValues.getFirst().toString())
                            .assetState(state).
                            build();

                        results.add(asset);
                    }
                }
            }
            return response.scrollId;
        } catch (IOException e) {
            LOGGER.error("Error in fetchAndScroll", e);
            throw new RuntimeException("Failed fetching assets", e);
        }
    }

    public OpenSearchResponse invokeAndCheck(HttpMethod method, String endpoint, String payLoad)
        throws IOException {
        var response = invoke(method, endpoint, payLoad);
        if (response.getStatusCode() < 200 || response.getStatusCode() > 299) {
            throw new IOException(
                String.format("Failed ElasticSearch request: %d; %s", response.getStatusCode(),
                    response.getStatusPhrase()));
        }
        return response;
    }

    private OpenSearchResponse invoke(HttpMethod method, String endPoint, String payLoad)
        throws IOException {
        String uri = endPoint;
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }

        var request = new Request(method.name(), uri);
        if (payLoad != null) {
            request.setEntity(new NStringEntity(payLoad, ContentType.APPLICATION_JSON));
        }

        return new OpenSearchResponse(getRestClient().performRequest(request));
    }

    private String getPrimaryIndexName(String dataSource, String type) {
        return String.format("%s_%s", dataSource, type);
    }

    private String getOpinionIndexName(String dataSource, String type) {
        return String.format("%s_%s_opinions", dataSource, type);
    }

    private RestClient getRestClient() {
        if (restClient == null) {
            var host = Configuration.get(ConfigConstants.ELASTICSEARCH_HOST);
            restClient = RestClient.builder(new HttpHost(host, 80)).build();
        }
        return restClient;
    }

    public <T> T classFromString(Class<T> clazz, String json) throws JsonProcessingException {
        return objectMapper.readValue(json, clazz);
    }


    public enum HttpMethod {
        GET("GET"), HEAD("HEAD"), POST("POST"), PUT("PUT"), DELETE("DELETE");

        public final String verb;

        HttpMethod(String name) {
            this.verb = name;
        }

        @Override
        public String toString() {
            return this.verb;
        }
    }
}
