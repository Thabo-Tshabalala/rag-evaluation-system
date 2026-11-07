package za.ac.cput.rag.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response DTO for GET /evaluation/comparison providing empirical, statistically defensible
 * metrics comparing Baseline (Control) vs. RAG (Treatment).
 */
public record ComparisonResponse(
        @JsonProperty("domain_filter") String domainFilter,
        @JsonProperty("matched_pairs_count") int matchedPairsCount,
        @JsonProperty("mean_baseline_faithfulness") double meanBaselineFaithfulness,
        @JsonProperty("mean_rag_faithfulness") double meanRagFaithfulness,
        @JsonProperty("absolute_faithfulness_delta") double absoluteFaithfulnessDelta,
        @JsonProperty("hallucination_reduction_rate") double hallucinationReductionRate,
        @JsonProperty("mean_context_precision") Double meanContextPrecision,
        @JsonProperty("mean_baseline_answer_relevancy") double meanBaselineAnswerRelevancy,
        @JsonProperty("mean_rag_answer_relevancy") double meanRagAnswerRelevancy,
        @JsonProperty("mean_answer_relevancy") double meanAnswerRelevancy,
        @JsonProperty("statistical_export") StatisticalExport statisticalExport,
        @JsonProperty("paired_comparisons") List<PairedComparisonItem> pairedComparisons
) {
    public record StatisticalExport(
            @JsonProperty("baseline_faithfulness_array") List<Double> baselineFaithfulnessArray,
            @JsonProperty("rag_faithfulness_array") List<Double> ragFaithfulnessArray,
            @JsonProperty("paired_faithfulness_deltas") List<Double> pairedFaithfulnessDeltas,
            @JsonProperty("baseline_relevancy_array") List<Double> baselineRelevancyArray,
            @JsonProperty("rag_relevancy_array") List<Double> ragRelevancyArray
    ) {
    }

    public record PairedComparisonItem(
            @JsonProperty("normalized_query") String normalizedQuery,
            @JsonProperty("raw_query") String rawQuery,
            @JsonProperty("domain") String domain,
            @JsonProperty("baseline_query_id") String baselineQueryId,
            @JsonProperty("rag_query_id") String ragQueryId,
            @JsonProperty("baseline_faithfulness") double baselineFaithfulness,
            @JsonProperty("rag_faithfulness") double ragFaithfulness,
            @JsonProperty("faithfulness_delta") double faithfulnessDelta,
            @JsonProperty("hallucination_reduction") double hallucinationReduction,
            @JsonProperty("rag_context_precision") Double ragContextPrecision,
            @JsonProperty("baseline_answer_relevancy") double baselineAnswerRelevancy,
            @JsonProperty("rag_answer_relevancy") double ragAnswerRelevancy
    ) {
    }
}
