package za.ac.cput.rag.memory;

import za.ac.cput.rag.evaluation.EvaluationResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe in-memory store for experiment runs (Baseline & RAG), session histories,
 * and LLM-as-a-Judge evaluation results.
 */
@Component
public class ExperimentStore {

    private final ConcurrentHashMap<String, ExperimentRecord> recordsByQueryId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<ExperimentRecord>> recordsBySessionId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EvaluationResponse> evaluationsByQueryId = new ConcurrentHashMap<>();
    private final List<ExperimentRecord> chronologicalRecords = new CopyOnWriteArrayList<>();

    public void saveRecord(ExperimentRecord record) {
        recordsByQueryId.put(record.queryId(), record);
        chronologicalRecords.add(record);
        if (record.sessionId() != null && !record.sessionId().isBlank()) {
            recordsBySessionId
                    .computeIfAbsent(record.sessionId(), k -> new CopyOnWriteArrayList<>())
                    .add(record);
        }
    }

    public Optional<ExperimentRecord> findRecordById(String queryId) {
        return Optional.ofNullable(recordsByQueryId.get(queryId));
    }

    public List<ExperimentRecord> getSessionRecords(String sessionId) {
        List<ExperimentRecord> list = recordsBySessionId.get(sessionId);
        if (list == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(list);
    }

    public List<ExperimentRecord> getAllRecords() {
        return new ArrayList<>(chronologicalRecords);
    }

    public void saveEvaluation(EvaluationResponse evaluation) {
        evaluationsByQueryId.put(evaluation.queryId(), evaluation);
    }

    public Optional<EvaluationResponse> findEvaluationByQueryId(String queryId) {
        return Optional.ofNullable(evaluationsByQueryId.get(queryId));
    }

    public Collection<EvaluationResponse> getAllEvaluations() {
        return Collections.unmodifiableCollection(evaluationsByQueryId.values());
    }
}
