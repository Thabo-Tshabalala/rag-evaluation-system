package za.ac.cput.rag.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final ChatClient chatClient;

    public RagController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder().topK(3).build())
                .build();

        this.chatClient = chatClientBuilder
                .defaultAdvisors(advisor)
                .build();
    }

    // A simple record to map the {"query": "..."} JSON body
    public record RagRequest(String query) {}

    @PostMapping("/ask") // Now a POST endpoint
    public String ask(@RequestBody RagRequest request) { // Reads the JSON body
        return chatClient.prompt()
                .user(request.query()) // Extracts the query string from the JSON
                .call()
                .content();
    }
}