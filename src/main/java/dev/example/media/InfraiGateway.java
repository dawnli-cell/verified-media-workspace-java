package dev.example.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InfraiGateway implements WorkspaceDirectory {
    private final String baseUrl;
    private final String key;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newHttpClient();

    public InfraiGateway(@Value("${infrai.base-url}") String baseUrl, ObjectMapper json) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY");
        this.json = json;
    }

    public JsonNode addDomain(String domain, String requestId) {
        return call("POST", "/v1/dns/domain/add", Map.of("domain", domain,
                "metadata", Map.of("idempotency_key", requestId)));
    }

    public JsonNode upsertTxt(String zoneId, String name, String content, String requestId) {
        return call("PUT", "/v1/dns/record/upsert", Map.of("zone_id", zoneId,
                "record_type", "TXT", "name", name, "content", content,
                "metadata", Map.of("idempotency_key", requestId)));
    }

    public JsonNode verifyDomain(String domain) {
        return call("POST", "/v1/dns/domain/verify", Map.of("domain", domain));
    }

    public JsonNode createUser(String email, String name, String domain, String requestId) {
        return call("POST", "/v1/auth/user/create", Map.of("email", email, "name", name,
                "metadata", Map.of("workspace_domain", domain), "idempotency_key", requestId));
    }

    private JsonNode call(String method, String path, Map<String, ?> body) {
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Authorization", "Bearer " + key)
                        .header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                        .timeout(Duration.ofSeconds(15)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 429 && attempt < 3) {
                    String retryAfter = response.headers().firstValue("Retry-After").orElse("");
                    long delay;
                    try { delay = Long.parseLong(retryAfter) * 1000L; }
                    catch (NumberFormatException ignored) { delay = 250L * (1L << attempt); }
                    Thread.sleep(Math.min(delay, 30000L));
                    continue;
                }
                // Decode the envelope first: a business rejection can arrive with HTTP 4xx.
                JsonNode envelope = json.readTree(response.body());
                if (!envelope.path("ok").asBoolean(false)) {
                    JsonNode error = envelope.path("error");
                    throw new InfraiException(response.statusCode(), error.path("code").asText("API_ERROR"),
                            error.path("message").asText("Request rejected"));
                }
                if (response.statusCode() >= 500) throw new InfraiException(502, "UPSTREAM_ERROR", "Upstream request failed");
                return envelope.path("data");
            } catch (IOException e) {
                throw new InfraiException(502, "TRANSPORT_ERROR", e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InfraiException(502, "INTERRUPTED", "Request interrupted");
            }
        }
        throw new InfraiException(429, "RATE_LIMITED", "Retry later");
    }

    public static class InfraiException extends RuntimeException {
        public final int status;
        public final String code;
        public InfraiException(int status, String code, String message) {
            super(message); this.status = status; this.code = code;
        }
    }
}
