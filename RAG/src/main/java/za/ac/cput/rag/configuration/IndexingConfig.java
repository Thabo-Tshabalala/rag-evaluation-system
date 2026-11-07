package za.ac.cput.rag.configuration;

import za.ac.cput.rag.service.PdfIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IndexingConfig {

    private static final Logger log = LoggerFactory.getLogger(IndexingConfig.class);

    @Bean
    @ConditionalOnProperty(name = "rag.indexing.enabled", havingValue = "true", matchIfMissing = false)
    public CommandLineRunner indexKnowledgeBase(PdfIndexingService indexingService) {
        return args -> {
            try {
                log.info("Starting knowledge base indexing...");
                int chunks = indexingService.indexPdf();
                log.info("PDF indexed successfully. Chunks created: {}", chunks);
            } catch (Exception e) {
                log.error("Failed to index PDF during startup: {}", e.getMessage(), e);
            }
        };
    }
}