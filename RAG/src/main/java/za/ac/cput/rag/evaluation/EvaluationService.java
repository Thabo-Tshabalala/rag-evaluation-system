package za.ac.cput.rag.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import za.ac.cput.rag.memory.ExperimentRecord;
import za.ac.cput.rag.memory.ExperimentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Automated LLM-as-a-Judge evaluation engine and comparative reliability statistics service
 * operating at deterministic temperature = 0.0.
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final ChatClient judgeChatClient;
    private final ExperimentStore experimentStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EvaluationService(
            @Qualifier("judgeChatClient") ChatClient judgeChatClient,
            ExperimentStore experimentStore) {
        this.judgeChatClient = judgeChatClient;
        this.experimentStore = experimentStore;
    }

    /**
     * Evaluates a logged query execution (Baseline or RAG) using Gemini at temperature = 0.0.
     */
    public EvaluationResponse evaluateQuery(String queryId) {
        if (queryId == null || queryId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field 'query_id' is required");
        }

        ExperimentRecord record = experimentStore.findRecordById(queryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No experiment record found for query_id: " + queryId));

        boolean isBaseline = "baseline".equalsIgnoreCase(record.mode());
        String judgePrompt = buildJudgePrompt(record, isBaseline);

        String rawJudgeOutput = judgeChatClient.prompt()
                .user(judgePrompt)
                .options(ChatOptions.builder().temperature(0.0).maxTokens(1024))
                .call()
                .content();

        EvaluationResponse response = parseJudgeOutput(record, isBaseline, rawJudgeOutput);
        experimentStore.saveEvaluation(response);
        return response;
    }

    /**
     * Computes aggregate comparative statistics matching Baseline and RAG runs on identical
     * or equivalent queries (optionally filtered by domain).
     */
    public ComparisonResponse computeComparison(String domainFilter) {
        List<ExperimentRecord> allRecords = experimentStore.getAllRecords();

        List<ExperimentRecord> filteredRecords = allRecords.stream()
                .filter(r -> domainFilter == null || domainFilter.isBlank()
                        || (r.domain() != null && r.domain().equalsIgnoreCase(domainFilter.trim())))
                .toList();

        // Group by normalized query string preserving insertion order
        Map<String, ExperimentRecord> latestBaselineByQuery = new LinkedHashMap<>();
        Map<String, ExperimentRecord> latestRagByQuery = new LinkedHashMap<>();

        for (ExperimentRecord record : filteredRecords) {
            String key = normalizeQueryKey(record.query());
            if ("baseline".equalsIgnoreCase(record.mode())) {
                latestBaselineByQuery.put(key, record);
            } else if ("rag".equalsIgnoreCase(record.mode())) {
                latestRagByQuery.put(key, record);
            }
        }

        List<ComparisonResponse.PairedComparisonItem> pairedItems = new ArrayList<>();
        List<Double> baselineFaithfulnessList = new ArrayList<>();
        List<Double> ragFaithfulnessList = new ArrayList<>();
        List<Double> faithfulnessDeltas = new ArrayList<>();
        List<Double> baselineRelevancyList = new ArrayList<>();
        List<Double> ragRelevancyList = new ArrayList<>();
        List<Double> ragContextPrecisionList = new ArrayList<>();

        for (Map.Entry<String, ExperimentRecord> entry : latestBaselineByQuery.entrySet()) {
            String normKey = entry.getKey();
            ExperimentRecord baselineRecord = entry.getValue();
            ExperimentRecord ragRecord = latestRagByQuery.get(normKey);

            if (ragRecord == null) {
                continue;
            }

            // Retrieve existing evaluation or automatically run judge evaluation if not yet scored
            EvaluationResponse baselineEval = experimentStore
                    .findEvaluationByQueryId(baselineRecord.queryId())
                    .orElseGet(() -> evaluateQuery(baselineRecord.queryId()));

            EvaluationResponse ragEval = experimentStore
                    .findEvaluationByQueryId(ragRecord.queryId())
                    .orElseGet(() -> evaluateQuery(ragRecord.queryId()));

            double baseFaith = round4(baselineEval.faithfulness() != null ? baselineEval.faithfulness() : 0.0);
            double ragFaith = round4(ragEval.faithfulness() != null ? ragEval.faithfulness() : 0.0);
            double delta = round4(ragFaith - baseFaith);
            // (1 - Baseline_faithfulness) - (1 - RAG_faithfulness)
            double hallucinationReduction = round4((1.0 - baseFaith) - (1.0 - ragFaith));

            Double ragCtxPrecision = ragEval.contextPrecision() != null ? round4(ragEval.contextPrecision()) : null;
            double baseRel = round4(baselineEval.answerRelevancy() != null ? baselineEval.answerRelevancy() : 0.0);
            double ragRel = round4(ragEval.answerRelevancy() != null ? ragEval.answerRelevancy() : 0.0);

            baselineFaithfulnessList.add(baseFaith);
            ragFaithfulnessList.add(ragFaith);
            faithfulnessDeltas.add(delta);
            baselineRelevancyList.add(baseRel);
            ragRelevancyList.add(ragRel);
            if (ragCtxPrecision != null) {
                ragContextPrecisionList.add(ragCtxPrecision);
            }

            pairedItems.add(new ComparisonResponse.PairedComparisonItem(
                    normKey,
                    ragRecord.query(),
                    ragRecord.domain(),
                    baselineRecord.queryId(),
                    ragRecord.queryId(),
                    baseFaith,
                    ragFaith,
                    delta,
                    hallucinationReduction,
                    ragCtxPrecision,
                    baseRel,
                    ragRel
            ));
        }

        double meanBaseFaith = round4(mean(baselineFaithfulnessList));
        double meanRagFaith = round4(mean(ragFaithfulnessList));
        double absDelta = round4(meanRagFaith - meanBaseFaith);
        double hallucinationReductionRate = round4((1.0 - meanBaseFaith) - (1.0 - meanRagFaith));
        Double meanCtxPrecision = ragContextPrecisionList.isEmpty()
                ? null
                : round4(mean(ragContextPrecisionList));
        double meanBaseRel = round4(mean(baselineRelevancyList));
        double meanRagRel = round4(mean(ragRelevancyList));
        double overallMeanRel = pairedItems.isEmpty()
                ? 0.0
                : round4((meanBaseRel + meanRagRel) / 2.0);

        ComparisonResponse.StatisticalExport export = new ComparisonResponse.StatisticalExport(
                baselineFaithfulnessList,
                ragFaithfulnessList,
                faithfulnessDeltas,
                baselineRelevancyList,
                ragRelevancyList
        );

        return new ComparisonResponse(
                (domainFilter == null || domainFilter.isBlank()) ? "ALL" : domainFilter.trim(),
                pairedItems.size(),
                meanBaseFaith,
                meanRagFaith,
                absDelta,
                hallucinationReductionRate,
                meanCtxPrecision,
                meanBaseRel,
                meanRagRel,
                overallMeanRel,
                export,
                pairedItems
        );
    }

    private String buildJudgePrompt(ExperimentRecord record, boolean isBaseline) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an impartial academic LLM-as-a-Judge evaluating a response generated by an AI system.\n")
                .append("Perform a rigorous, deterministic evaluation and return ONLY a valid JSON object (no markdown fences, no extra prose).\n\n")
                .append("PIPELINE MODE: ").append(record.mode()).append("\n")
                .append("DOMAIN: ").append(record.domain()).append("\n")
                .append("USER QUERY:\n").append(record.query()).append("\n\n")
                .append("MODEL RESPONSE:\n").append(record.response()).append("\n\n");

        if (isBaseline) {
            sb.append("RETRIEVED CHUNKS: [NONE - Baseline standalone LLM with zero retrieved policy documents]\n\n")
                    .append("SCORING RUBRIC FOR BASELINE MODE:\n")
                    .append("1. context_precision: Must be strictly null because no chunks were retrieved.\n")
                    .append("2. faithfulness (0.00 to 1.00): Faithfulness measures the proportion of policy claims verified by retrieved enterprise context. Because zero chunks were retrieved in baseline mode, any specific numbers, durations, or policy assertions are ungrounded parametric guesses (score 0.00 to 0.15), and even a refusal or generic statutory fallback provides zero verified enterprise facts (score at most 0.20).\n")
                    .append("3. answer_relevancy (0.00 to 1.00): Directness and pertinence of the response to the user's question (penalize if generic or unverified against enterprise policy).\n");
        } else {
            sb.append("RETRIEVED CHUNKS:\n");
            List<String> chunks = record.retrievedChunks();
            if (chunks == null || chunks.isEmpty()) {
                sb.append("[0 chunks retrieved]\n");
            } else {
                for (int i = 0; i < chunks.size(); i++) {
                    sb.append("Chunk ").append(i + 1).append(": ").append(chunks.get(i)).append("\n---\n");
                }
            }
            sb.append("\nSCORING RUBRIC FOR RAG MODE:\n")
                    .append("1. context_precision (0.00 to 1.00): Signal-to-noise ratio of the retrieved chunks (proportion of retrieved chunks that contain information relevant to answering the query).\n")
                    .append("2. faithfulness (0.00 to 1.00): Percentage of factual claims in the MODEL RESPONSE that can be directly verified from the RETRIEVED CHUNKS.\n")
                    .append("3. answer_relevancy (0.00 to 1.00): Directness, completeness, and pertinence of the MODEL RESPONSE to the USER QUERY.\n");
        }

        sb.append("\nReturn ONLY a JSON object with this exact schema:\n")
                .append("{\n")
                .append("  \"context_precision\": ").append(isBaseline ? "null" : "0.80").append(",\n")
                .append("  \"faithfulness\": 0.95,\n")
                .append("  \"answer_relevancy\": 0.90,\n")
                .append("  \"reasoning\": \"Concise step-by-step verification of claims against evidence.\"\n")
                .append("}\n");

        return sb.toString();
    }

    private EvaluationResponse parseJudgeOutput(ExperimentRecord record, boolean isBaseline, String rawOutput) {
        try {
            String cleaned = cleanJson(rawOutput);
            JsonNode root = objectMapper.readTree(cleaned);

            Double contextPrecision = null;
            if (!isBaseline && root.has("context_precision") && !root.get("context_precision").isNull()) {
                contextPrecision = clampScore(root.get("context_precision").asDouble());
            }

            double faithfulness = root.has("faithfulness") && !root.get("faithfulness").isNull()
                    ? clampScore(root.get("faithfulness").asDouble())
                    : 0.0;

            double answerRelevancy = root.has("answer_relevancy") && !root.get("answer_relevancy").isNull()
                    ? clampScore(root.get("answer_relevancy").asDouble())
                    : 0.0;

            String reasoning = root.has("reasoning") && !root.get("reasoning").isNull()
                    ? root.get("reasoning").asText()
                    : "Evaluated via Gemini Judge at temperature=0.0.";

            return new EvaluationResponse(
                    record.queryId(),
                    record.mode(),
                    contextPrecision,
                    faithfulness,
                    answerRelevancy,
                    reasoning
            );
        } catch (Exception e) {
            log.warn("Failed to parse structured JSON from Judge LLM output, falling back to safe extraction: {}", e.getMessage());
            return new EvaluationResponse(
                    record.queryId(),
                    record.mode(),
                    isBaseline ? null : 0.5,
                    0.5,
                    0.5,
                    "Raw judge output: " + (rawOutput != null ? rawOutput : "empty")
            );
        }
    }

    private String cleanJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        int startBrace = trimmed.indexOf('{');
        int endBrace = trimmed.lastIndexOf('}');
        if (startBrace != -1 && endBrace != -1 && endBrace > startBrace) {
            return trimmed.substring(startBrace, endBrace + 1);
        }
        return trimmed.trim();
    }

    private String normalizeQueryKey(String query) {
        if (query == null) {
            return "";
        }
        return query.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ");
    }

    private double clampScore(double val) {
        double clamped = Math.max(0.0, Math.min(1.0, val));
        return round4(clamped);
    }

    private double mean(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (Double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
