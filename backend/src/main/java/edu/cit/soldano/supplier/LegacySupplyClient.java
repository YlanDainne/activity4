package edu.cit.soldano.supplier;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import edu.cit.soldano.channel.ClientInstanceProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
class LegacySupplyClient {

    private final String baseUrl = "https://legacysupply.onrender.com/api/v1";
    private final String clientId;
    private final String apiKey;
    private final ClientInstanceProvider instance;

    private final HttpClient httpClient;
    private final XmlMapper xmlMapper = new XmlMapper();
    private String cachedToken = null;

    LegacySupplyClient(
        @Value("${legacy.client-id:${LS_CLIENT_ID:}}") String clientId,
        @Value("${legacy.api-key:${LS_API_KEY:}}") String apiKey,
        ClientInstanceProvider instance
    ) {
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.instance = instance;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    synchronized String getOrRefreshToken() {
        if (!instance.callsAllowed()) {
            throw new IllegalStateException("Initial Tiangge heartbeat has not completed");
        }
        if (cachedToken != null) return cachedToken;
        return login();
    }

    synchronized String login() {
        try {
            var reqBody = xmlMapper.writeValueAsString(new LegacyXmlPayloads.AuthRequest(clientId, apiKey));
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/auth/token"))
                    .timeout(Duration.ofSeconds(3))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE)
                    .header("X-Client-Instance", instance.instanceId())
                    .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                    .build();

            var res = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                var auth = xmlMapper.readValue(res.body(), LegacyXmlPayloads.AuthResponse.class);
                this.cachedToken = auth.sessionToken();
                return this.cachedToken;
            }
            throw new IllegalStateException("Authentication failed with HTTP " + res.statusCode() + ": " + res.body());
        } catch (Exception e) {
            throw new RuntimeException("LegacySupply login error: " + e.getMessage(), e);
        }
    }

    LegacyXmlPayloads.PoResponse submitPurchaseOrder(String requestId, LegacyXmlPayloads.PoRequest poRequest) throws Exception {
        return executeWithRetry(() -> {
            String token = getOrRefreshToken();
            String xmlPayload = xmlMapper.writeValueAsString(poRequest);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/purchase-orders"))
                    .timeout(Duration.ofSeconds(3))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE)
                    .header("X-LS-Session", token)
                    .header("X-Client-Instance", instance.instanceId())
                    .header("X-Request-Id", requestId)
                    .POST(HttpRequest.BodyPublishers.ofString(xmlPayload))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 401) {
                this.cachedToken = null;
                token = login();
                req = HttpRequest.newBuilder(req, (k, v) -> true)
                        .header("X-LS-Session", token)
                        .build();
                res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            }

            if (res.statusCode() == 200 || res.statusCode() == 201) {
                return xmlMapper.readValue(res.body(), LegacyXmlPayloads.PoResponse.class);
            }
            throw new RuntimeException("LegacySupply dispatch failed with HTTP " + res.statusCode() + ": " + res.body());
        });
    }

    LegacyXmlPayloads.StatusResponse checkOrderStatus(String poNumber) throws Exception {
        return executeWithRetry(() -> {
            String token = getOrRefreshToken();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/purchase-orders/" + poNumber))
                    .timeout(Duration.ofSeconds(3))
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE)
                    .header("X-LS-Session", token)
                    .header("X-Client-Instance", instance.instanceId())
                    .GET()
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 401) {
                this.cachedToken = null;
                token = login();
                req = HttpRequest.newBuilder(req, (k, v) -> true)
                        .header("X-LS-Session", token)
                        .build();
                res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            }

            if (res.statusCode() == 200) {
                return xmlMapper.readValue(res.body(), LegacyXmlPayloads.StatusResponse.class);
            }
            throw new RuntimeException("Query failed for PO " + poNumber + ": HTTP " + res.statusCode());
        });
    }

    private <T> T executeWithRetry(CallableWithException<T> action) throws Exception {
        int attempts = 0;
        long backoffMs = 500;
        Exception lastException = null;

        while (attempts < 3) {
            attempts++;
            try {
                return action.call();
            } catch (Exception e) {
                lastException = e;
                if (attempts >= 3) break;
                Thread.sleep(backoffMs);
                backoffMs *= 2;
            }
        }
        throw lastException;
    }

    @FunctionalInterface
    interface CallableWithException<T> {
        T call() throws Exception;
    }

}