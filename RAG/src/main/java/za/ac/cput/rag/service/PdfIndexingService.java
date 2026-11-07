package za.ac.cput.rag.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfIndexingService {

    private final VectorStore vectorStore;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public PdfIndexingService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public int indexPdf() {
        try {

            Resource[] resources = resolver.getResources("classpath:knowledge-base/*.pdf");

            List<Document> allPages = new ArrayList<>();

            for (Resource resource : resources) {
                var pdfReader = new PagePdfDocumentReader(resource);
                allPages.addAll(pdfReader.read());
            }

            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(800)
                    .withMinChunkLengthToEmbed(350)
                    .build();

            List<Document> chunks = splitter.split(allPages);

            vectorStore.add(chunks);

            return chunks.size();

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load PDF resources from classpath", e);
        }
    }
}