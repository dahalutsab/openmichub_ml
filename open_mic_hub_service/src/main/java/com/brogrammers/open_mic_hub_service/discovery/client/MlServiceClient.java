package com.brogrammers.open_mic_hub_service.discovery.client;

import com.brogrammers.open_mic_hub_service.discovery.dto.DiscoveryRequest;
import com.brogrammers.open_mic_hub_service.discovery.dto.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Talks to the ML service.
 *
 * <p>Discovery is an enhancement, not a dependency: every call returns an empty
 * {@link Optional} rather than throwing if the service is slow or down, and the
 * caller falls back to the plain artist listing. A machine-learning container
 * being unavailable must not stop people browsing the site.
 */
@Component
@Slf4j
public class MlServiceClient {

    private final WebClient webClient;
    private final Duration timeout;

    public MlServiceClient(WebClient.Builder builder,
                           @Value("${ml.service.url:http://127.0.0.1:8000}") String baseUrl,
                           @Value("${ml.service.timeout-ms:4000}") long timeoutMs) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.timeout = Duration.ofMillis(timeoutMs);
        log.info("ML service configured at {} with a {}ms timeout", baseUrl, timeoutMs);
    }

    /** Semantic search followed by learned ranking. */
    public Optional<SearchResult> search(DiscoveryRequest request) {
        return post("/search", request);
    }

    /** Ranking with no text query, for browse and filter surfaces. */
    public Optional<SearchResult> recommend(DiscoveryRequest request) {
        return post("/recommend", request);
    }

    /**
     * Artists nearest to one artist in embedding space.
     *
     * <p>Returns an empty list rather than an error when the ML service is down: a
     * "more like this" strip is a nicety, and the profile page it sits on must
     * still render without it.
     */
    public List<Map<String, Object>> similarArtists(long artistId, int limit) {
        return get("/artists/" + artistId + "/similar?limit=" + limit, "similar");
    }

    /** The segmentation overview, for the admin board. */
    public List<Map<String, Object>> segments() {
        return get("/segments", "segments");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> get(String path, String key) {
        try {
            Map<String, Object> body = webClient.get()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(timeout);
            if (body == null) {
                return List.of();
            }
            Object value = body.get(key);
            return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
        } catch (Exception e) {
            log.warn("ML service call to {} failed ({}); returning nothing", path, e.getMessage());
            return List.of();
        }
    }

    private Optional<SearchResult> post(String path, DiscoveryRequest request) {
        try {
            SearchResult result = webClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SearchResult.class)
                    .block(timeout);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.warn("ML service call to {} failed ({}); falling back to the plain listing",
                    path, e.getMessage());
            return Optional.empty();
        }
    }
}
