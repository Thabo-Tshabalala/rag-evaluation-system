package za.ac.cput.rag.evaluation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing the LLM-as-a-Judge evaluation and empirical reliability comparison endpoints:
 * - POST /evaluation/run
 * - GET /evaluation/comparison
 */
@RestController
@RequestMapping("/evaluation")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/run")
    public ResponseEntity<EvaluationResponse> runEvaluation(@RequestBody EvaluationRequest request) {
        String queryId = request != null ? request.queryId() : null;
        EvaluationResponse response = evaluationService.evaluateQuery(queryId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/comparison")
    public ResponseEntity<ComparisonResponse> getComparison(
            @RequestParam(value = "domain", required = false) String domain) {
        ComparisonResponse comparison = evaluationService.computeComparison(domain);
        return ResponseEntity.ok(comparison);
    }
}
