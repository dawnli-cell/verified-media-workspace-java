package dev.example.media;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkspaceController {
    private final WorkspaceJoin join;
    public WorkspaceController(WorkspaceJoin join) { this.join = join; }

    @PostMapping("/workspaces/prepare")
    public WorkspaceJoin.SetupResult prepare(@RequestBody WorkspaceJoin.DomainSetup input) { return join.prepare(input); }

    @PostMapping("/workspaces/join")
    public WorkspaceJoin.Joined join(@RequestBody WorkspaceJoin.Employee input) { return join.join(input); }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InfraiGateway.InfraiException.class)
    public ResponseEntity<Map<String, String>> upstream(InfraiGateway.InfraiException e) {
        int status = e.status >= 400 && e.status < 500 ? e.status : 502;
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(Map.of("code", e.code, "error", e.getMessage()));
    }
}
