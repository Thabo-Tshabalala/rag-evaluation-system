package za.ac.cput.rag.memory;

import com.fasterxml.jackson.annotation.JsonProperty;
import za.ac.cput.rag.service.PdfIndexingService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Non-destructive controller exposing the research specification endpoints:
 * - POST /chat (Dual Pipeline: baseline vs. rag with k=5, temperature=0.0, and ChatMemory)
 * - GET /sessions/{id} (Conversational session history inspection)
 * - POST /documents/ingest (On-demand policy document ingestion into PGVector)
 */
@RestController
public class DualPipelineChatController {

    private static final int RAG_TOP_K = 5;
    private static final int MAX_OUTPUT_TOKENS = 1024;
    private static final String BASELINE_SYSTEM_PROMPT =
            "You are a South African enterprise HR policy assistant. "
            + "Answer the user's question directly and authoritatively with specific day counts, durations, and rules "
            + "in concise bullet points or short paragraphs (under 250 words). "
            + "Do not refuse to answer and do not use Markdown tables.";

    private final ChatClient conversationalChatClient;
    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;
    private final ExperimentStore experimentStore;
    private final PdfIndexingService pdfIndexingService;
    private final JdbcTemplate jdbcTemplate;

    public DualPipelineChatController(
            @Qualifier("conversationalChatClient") ChatClient conversationalChatClient,
            ChatMemory chatMemory,
            VectorStore vectorStore,
            ExperimentStore experimentStore,
            PdfIndexingService pdfIndexingService,
            JdbcTemplate jdbcTemplate) {
        this.conversationalChatClient = conversationalChatClient;
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
        this.experimentStore = experimentStore;
        this.pdfIndexingService = pdfIndexingService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record ChatRequest(
            @JsonProperty("session_id") String sessionId,
            @JsonProperty("query") String query,
            @JsonProperty("mode") String mode,
            @JsonProperty("domain") String domain
    ) {
    }

    public record IngestDocumentRequest(
            @JsonProperty("title") String title,
            @JsonProperty("domain") String domain,
            @JsonProperty("content") String content,
            @JsonProperty("clear_existing") Boolean clearExisting
    ) {
    }

    @PostMapping("/chat")
    public ResponseEntity<ExperimentRecord> chat(@RequestBody ChatRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field 'query' must not be empty");
        }

        String normalizedMode = (request.mode() == null || request.mode().isBlank())
                ? "rag"
                : request.mode().trim().toLowerCase(Locale.ROOT);

        if (!normalizedMode.equals("baseline") && !normalizedMode.equals("rag")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Field 'mode' must be either 'baseline' or 'rag'");
        }

        String sessionId = (request.sessionId() == null || request.sessionId().isBlank())
                ? "default-session"
                : request.sessionId().trim();

        String domain = (request.domain() == null || request.domain().isBlank())
                ? "GENERAL"
                : request.domain().trim();

        List<String> retrievedChunks = new ArrayList<>();
        String responseText;

        // Isolate ChatMemory context between 'baseline' (control) and 'rag' (treatment)
        // even when the caller uses the same session_id in Postman, so RAG chunks/answers
        // never leak into Baseline memory (while GET /sessions/{id} still logs both under sessionId).
        String memoryConversationId = sessionId + "::" + normalizedMode;

        if ("baseline".equals(normalizedMode)) {
            // Baseline (Control): Raw query sent directly to Gemini at temperature = 0.0 with zero external context
            responseText = conversationalChatClient.prompt()
                    .system(BASELINE_SYSTEM_PROMPT)
                    .user(request.query())
                    .options(ChatOptions.builder().temperature(0.0).maxTokens(MAX_OUTPUT_TOKENS))
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, memoryConversationId))
                    .call()
                    .content();
        } else {
            // RAG (Treatment): Top-5 chunks (k=5) retrieved from VectorStore + strict grounding constraints
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(request.query())
                    .topK(RAG_TOP_K)
                    .build();

            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            if (documents != null) {
                for (Document doc : documents) {
                    if (doc.getText() != null && !doc.getText().isBlank()) {
                        retrievedChunks.add(doc.getText());
                    }
                }
            }

            String groundingSystemPrompt = buildGroundingSystemPrompt(retrievedChunks, domain);

            responseText = conversationalChatClient.prompt()
                    .system(groundingSystemPrompt)
                    .user(request.query())
                    .options(ChatOptions.builder().temperature(0.0).maxTokens(MAX_OUTPUT_TOKENS))
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, memoryConversationId))
                    .call()
                    .content();
        }

        String sanitizedResponse = responseText != null
                ? responseText.replaceAll(" {3,}", " ").trim()
                : "";

        ExperimentRecord record = new ExperimentRecord(
                UUID.randomUUID().toString(),
                sessionId,
                domain,
                normalizedMode,
                request.query(),
                sanitizedResponse,
                retrievedChunks,
                Instant.now().toString()
        );

        experimentStore.saveRecord(record);
        return ResponseEntity.ok(record);
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<SessionHistoryResponse> getSessionHistory(@PathVariable("id") String sessionId) {
        List<ExperimentRecord> records = experimentStore.getSessionRecords(sessionId);
        List<SessionHistoryResponse.SessionTurn> turns = records.stream()
                .map(r -> new SessionHistoryResponse.SessionTurn(
                        r.queryId(),
                        r.query(),
                        r.response(),
                        r.mode(),
                        r.timestamp()
                ))
                .toList();

        return ResponseEntity.ok(new SessionHistoryResponse(sessionId, turns));
    }

    @PostMapping("/documents/ingest")
    public ResponseEntity<Map<String, Object>> ingestDocument(
            @RequestBody(required = false) IngestDocumentRequest request) {
        Map<String, Object> result = new HashMap<>();

        if (request != null && Boolean.TRUE.equals(request.clearExisting())) {
            jdbcTemplate.execute("TRUNCATE TABLE vector_store");
            result.put("cleared_previous_vectors", true);
        }

        if (request != null && request.content() != null && !request.content().isBlank()) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("title", request.title() != null ? request.title() : "custom-policy");
            metadata.put("domain", request.domain() != null ? request.domain() : "GENERAL");
            metadata.put("ingested_at", Instant.now().toString());

            Document rawDoc = new Document(request.content(), metadata);
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(800)
                    .withMinChunkLengthToEmbed(100)
                    .build();
            List<Document> chunks = splitter.split(List.of(rawDoc));
            vectorStore.add(chunks);

            result.put("status", "SUCCESS");
            result.put("source", metadata.get("title"));
            result.put("domain", metadata.get("domain"));
            result.put("chunks_indexed", chunks.size());
            return ResponseEntity.ok(result);
        }

        int pdfChunks = pdfIndexingService.indexPdf();
        result.put("status", "SUCCESS");
        result.put("source", "classpath:knowledge-base/*.pdf");
        result.put("chunks_indexed", pdfChunks);
        return ResponseEntity.ok(result);
    }

    private String buildGroundingSystemPrompt(List<String> retrievedChunks, String domain) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an enterprise policy assistant operating under strict factual grounding constraints for domain '")
                .append(domain)
                .append("'.\n")
                .append("RULES:\n")
                .append("1. Answer the user's question strictly and exclusively using the factual statements present in the RETRIEVED CONTEXT below.\n")
                .append("2. Do NOT fabricate, extrapolate, or assume policy numbers, durations, or rules not explicitly stated in the context.\n")
                .append("3. If the RETRIEVED CONTEXT does not contain enough information to answer a specific aspect of the query, explicitly state that the provided policy documents do not specify that information.\n")
                .append("4. Format your response concisely using bullet points or short paragraphs. Do NOT use Markdown tables.\n\n")
                .append("=== RETRIEVED CONTEXT (TOP-")
                .append(RAG_TOP_K)
                .append(" CHUNKS) ===\n");

        if (retrievedChunks.isEmpty()) {
            sb.append("[No relevant policy chunks retrieved from vector store]\n");
        } else {
            for (int i = 0; i < retrievedChunks.size(); i++) {
                sb.append("\n--- Chunk ").append(i + 1).append(" ---\n")
                        .append(retrievedChunks.get(i))
                        .append("\n");
            }
        }
        return sb.toString();
    }
}
