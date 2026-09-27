package dev.example.media;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceJoin {
    private final WorkspaceDirectory gateway;

    public WorkspaceJoin(WorkspaceDirectory gateway) { this.gateway = gateway; }

    public record DomainSetup(String domain, String txtName, String txtContent, String requestId) {}
    public record SetupResult(String domain, String zoneId, String txtName) {}
    public record Employee(String domain, String email, String name, String requestId) {}
    public record Joined(String workspace, String employee, String assetStage, String jobStage, String deliveryStage) {}

    public SetupResult prepare(DomainSetup input) {
        if (input.domain() == null || input.domain().isBlank() || input.txtName() == null || input.txtName().isBlank()
                || input.txtContent() == null || input.txtContent().isBlank() || input.requestId() == null || input.requestId().isBlank())
            throw new IllegalArgumentException("Domain, TXT name, TXT content and request ID are required");
        JsonNode domain = gateway.addDomain(input.domain(), input.requestId());
        String zoneId = domain.path("zone_id").asText();
        if (zoneId.isBlank()) throw new IllegalStateException("Domain response needs zone_id");
        gateway.upsertTxt(zoneId, input.txtName(), input.txtContent(), input.requestId());
        return new SetupResult(input.domain(), zoneId, input.txtName());
    }

    public Joined join(Employee input) {
        if (input.domain() == null || input.email() == null || input.name() == null || input.requestId() == null
                || input.requestId().isBlank() || input.name().isBlank()
                || !input.email().toLowerCase(java.util.Locale.ROOT).endsWith("@" + input.domain().toLowerCase(java.util.Locale.ROOT)))
            throw new IllegalArgumentException("Employee email must belong to the requested domain; name and request ID are required");
        JsonNode proof = gateway.verifyDomain(input.domain());
        if (!proof.path("verified").asBoolean(false))
            throw new IllegalArgumentException("Domain ownership must be verified before joining");
        gateway.createUser(input.email(), input.name(), input.domain(), input.requestId());
        return new Joined(input.domain(), input.email(), "awaiting_ingestion", "awaiting_processing", "awaiting_creator_delivery");
    }
}
