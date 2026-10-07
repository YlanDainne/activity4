package edu.cit.soldano.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
class TianggeClient {
    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final ClientInstanceProvider instance;
    private final ObjectMapper mapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    TianggeClient(
            @Value("${tiangge.base-url:https://legacysupply.onrender.com/tiangge/v1}") String baseUrl,
            @Value("${tiangge.client-id:${TIANGGE_CLIENT_ID:}}") String clientId,
            @Value("${tiangge.api-key:${TIANGGE_API_KEY:}}") String apiKey,
            ClientInstanceProvider instance,
            ObjectMapper mapper) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.instance = instance;
        this.mapper = mapper;
    }

    boolean configured() {
        return !clientId.isBlank() && !apiKey.isBlank();
    }

    void heartbeat() throws Exception {
        send("POST", "/instances/heartbeat", new TianggePayloads.HeartbeatRequest(
                "shop-app", instance.startedAt().toString(),
                Duration.between(instance.startedAt(), java.time.Instant.now()).toSeconds()), String.class);
    }

    void publishListings(List<TianggePayloads.Listing> listings) throws Exception {
        send("PUT", "/listings", listings, String.class);
    }

    void publishStock(List<TianggePayloads.Stock> stock) throws Exception {
        send("PUT", "/stock", stock, String.class);
    }

    TianggePayloads.FeedResponse feed(long cursor) throws Exception {
        return send("GET", "/feed?after=" + cursor + "&limit=50", null, TianggePayloads.FeedResponse.class);
    }

    void decide(String orderId, String decision, String shopOrderId, String reason) throws Exception {
        send("POST", "/orders/" + orderId + "/decision",
                new TianggePayloads.DecisionRequest(decision, shopOrderId, reason), String.class);
    }

    void resolve(String orderId, String status) throws Exception {
        send("POST", "/orders/" + orderId + "/resolution",
                new TianggePayloads.ResolutionRequest(status), String.class);
    }

    void confirmCancellation(String orderId) throws Exception {
        send("POST", "/orders/" + orderId + "/cancellation",
                new TianggePayloads.CancellationRequest(true), String.class);
    }

    private <T> T send(String method, String path, Object body, Class<T> responseType) throws Exception {
        String payload = body == null ? "" : mapper.writeValueAsString(body);
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .timeout(Duration.ofSeconds(3))
                        .header("X-Client-Id", clientId)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("X-Client-Instance", instance.instanceId())
                        .header("Accept", "application/json");
                if (body != null) {
                    builder.header("Content-Type", "application/json")
                            .method(method, HttpRequest.BodyPublishers.ofString(payload));
                } else {
                    builder.method(method, HttpRequest.BodyPublishers.noBody());
                }
                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    if (responseType == String.class || response.body().isBlank()) return null;
                    return mapper.readValue(response.body(), responseType);
                }
                if (response.statusCode() != 503 && response.statusCode() != 429) {
                    throw new IllegalStateException("Tiangge HTTP " + response.statusCode() + ": " + response.body());
                }
                last = new IllegalStateException("Tiangge temporary HTTP " + response.statusCode());
            } catch (Exception exception) {
                last = exception;
            }
            if (attempt < 3) Thread.sleep(250L * attempt);
        }
        throw last;
    }
}
