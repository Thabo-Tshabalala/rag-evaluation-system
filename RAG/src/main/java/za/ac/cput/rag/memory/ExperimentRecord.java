package za.ac.cput.rag.memory;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Immutable record representing a single Baseline or RAG execution logged to the ExperimentStore.
 */
public record ExperimentRecord(
        @JsonProperty("query_id") String queryId,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("domain") String domain,
        @JsonProperty("mode") String mode,
        @JsonProperty("query") String query,
        @JsonProperty("response") String response,
        @JsonProperty("retrieved_chunks") List<String> retrievedChunks,
        @JsonProperty("timestamp") String timestamp
) {
}
