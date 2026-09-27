package dev.example.media;

import com.fasterxml.jackson.databind.JsonNode;

public interface WorkspaceDirectory {
    JsonNode addDomain(String domain, String requestId);
    JsonNode upsertTxt(String zoneId, String name, String content, String requestId);
    JsonNode verifyDomain(String domain);
    JsonNode createUser(String email, String name, String domain, String requestId);
}
