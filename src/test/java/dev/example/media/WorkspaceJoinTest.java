package dev.example.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceJoinTest {
    @Test void onlyVerifiedCompanyEmployeesJoin() throws Exception {
        ObjectMapper json = new ObjectMapper();
        class Directory implements WorkspaceDirectory {
            boolean verified;
            int created;
            public JsonNode addDomain(String domain, String id) { throw new AssertionError(); }
            public JsonNode upsertTxt(String zone, String name, String content, String id) { throw new AssertionError(); }
            public JsonNode verifyDomain(String domain) {
                return json.valueToTree(java.util.Map.of("verified", verified));
            }
            public JsonNode createUser(String email, String name, String domain, String id) {
                assertEquals("editor@studio.example", email);
                assertEquals("join-42", id);
                created++;
                return json.createObjectNode();
            }
        }
        Directory directory = new Directory();
        WorkspaceJoin join = new WorkspaceJoin(directory);
        WorkspaceJoin.Employee employee = new WorkspaceJoin.Employee("studio.example", "editor@studio.example", "Editor", "join-42");
        assertThrows(IllegalArgumentException.class, () -> join.join(employee));
        assertEquals(0, directory.created);
        directory.verified = true;
        WorkspaceJoin.Joined result = join.join(employee);
        assertEquals("awaiting_ingestion", result.assetStage());
        assertEquals("awaiting_creator_delivery", result.deliveryStage());
        assertEquals(1, directory.created);
    }
}
