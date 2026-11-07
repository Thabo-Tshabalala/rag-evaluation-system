package za.ac.cput.rag.memory;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response DTO for GET /sessions/{id}.
 */
public record SessionHistoryResponse(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("history") List<SessionTurn> history
) {
    public record SessionTurn(
            @JsonProperty("query_id") String queryId,
            @JsonProperty("query") String query,
            @JsonProperty("response") String response,
            @JsonProperty("mode") String mode,
            @JsonProperty("timestamp") String timestamp
    ) {
    }
}
