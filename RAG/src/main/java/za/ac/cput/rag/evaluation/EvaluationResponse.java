package za.ac.cput.rag.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for POST /evaluation/run.
 */
public record EvaluationResponse(
        @JsonProperty("query_id") String queryId,
        @JsonProperty("mode") String mode,
        @JsonProperty("context_precision") Double contextPrecision,
        @JsonProperty("faithfulness") Double faithfulness,
        @JsonProperty("answer_relevancy") Double answerRelevancy,
        @JsonProperty("reasoning") String reasoning
) {
}
