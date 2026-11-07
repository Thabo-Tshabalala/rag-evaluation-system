package za.ac.cput.rag.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for POST /evaluation/run.
 */
public record EvaluationRequest(
        @JsonProperty("query_id") String queryId
) {
}
