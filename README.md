# Enterprise RAG Service & LLM-as-a-Judge Evaluation Engine

[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.1-blue.svg)](https://spring.io/projects/spring-ai)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791.svg)](https://github.com/pgvector/pgvector)
[![Google GenAI](https://img.shields.io/badge/LLM-Gemini%202.5%20Flash-8E75C2.svg)](https://ai.google.dev/)

An enterprise-grade Retrieval-Augmented Generation (RAG) system engineered to mitigate hallucinations in proprietary policy question-answering. Built with **Spring Boot 4.1.2**, **Spring AI 2.0.1**, **PostgreSQL with pgvector**, and **Google Gemini 2.5 Flash**, the system implements semantic chunk indexing, dual-mode execution (Parametric Baseline vs. Grounded RAG), multi-turn contextual memory, and an automated **LLM-as-a-Judge** evaluation engine assessing the RAG Triad metrics (Context Precision, Faithfulness, Answer Relevancy).

---

## 📌 Key Architectural Capabilities

- **Semantic Document Ingestion**: Ingests enterprise PDF manuals, splits content with `TokenTextSplitter`, computes embeddings with Google GenAI Embeddings (`text-embedding-004`), and stores vectors in PostgreSQL `pgvector` using cosine similarity (`1 - (vector <=> query)`).
- **Dual Pipeline Execution**:
  - `mode: "baseline"`: Direct zero-shot generation using LLM parametric weights (serves as the experimental control group).
  - `mode: "rag"`: Context-augmented generation using semantic vector retrieval with similarity threshold filtering and multi-document synthesis.
- **In-Memory Multi-Turn Conversational Session Memory**: Thread-safe (`ConcurrentHashMap`) conversation history tracking enabling contextual follow-up reasoning across sequential queries.
- **Automated LLM-as-a-Judge Evaluator**: Quantitative evaluation microservice benchmarking model outputs against retrieved context and reference ground truth, producing Faithfulness, Relevancy, Context Precision, and hallucination reduction deltas.
- **Empirical Statistical Export**: Generates paired observation vectors across control and treatment queries for statistical significance verification (Paired $t$-test, Wilcoxon Signed-Rank Test, Cohen's $d$).

---

## 🏗️ System Architecture

```mermaid
flowchart TD
    User([Client / Postman]) -->|HTTP POST /chat| Controller[RagController]
    User -->|HTTP POST /documents/ingest| Ingestion[PdfIndexingService]
    User -->|HTTP POST /evaluation/run| EvalService[EvaluationService]
    User -->|HTTP GET /evaluation/comparison| EvalService

    subgraph Data Tier
        PDF[(Enterprise Policy PDF)] -->|Apache PDFBox| Ingestion
        Ingestion -->|TokenTextSplitter| Chunks[Document Chunks]
        Chunks -->|Google GenAI Embeddings| PGVector[(PostgreSQL + pgvector)]
    end

    subgraph RAG Pipeline
        Controller -->|Query Embeddings| PGVector
        PGVector -->|Top-K Context| ContextFilter[Cosine Similarity Filter]
        ContextFilter --> AugmentedPrompt[Augmented Context Prompt]
        AugmentedPrompt --> GeminiLLM[Google Gemini 2.5 Flash]
    end

    subgraph Conversational Memory
        Controller <-->|Session ID| MemoryStore[InMemorySessionMemoryService]
    end

    subgraph Evaluation Engine
        EvalService --> GeminiJudge[LLM-as-a-Judge]
        GeminiJudge --> Metrics[Context Precision | Faithfulness | Relevancy]
    end
```

---

## ⚙️ Prerequisites & Tech Stack

- **Java**: JDK 21+
- **Build Tool**: Apache Maven 3.9+
- **Database**: PostgreSQL 16+ with `pgvector` extension enabled
- **Model API**: Google Gemini API Key (`GEMINI_API_KEY`)
- **Package Base**: `za.ac.cput.rag`

---

## 🚀 Setup & Installation

### 1. Clone the Repository
```bash
git clone https://github.com/<your-username>/spring-ai-rag-evaluation-system.git
cd spring-ai-rag-evaluation-system/RAG
```

### 2. Configure Database & Environment Variables
Ensure PostgreSQL is running with the `pgvector` extension created:
```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

Configure your `src/main/resources/application.properties`:
```properties
spring.application.name=RAG

# PostgreSQL with pgvector configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/ragdb
spring.datasource.username=postgres
spring.datasource.password=your_postgres_password
spring.ai.vectorstore.pgvector.initialize-schema=true
spring.ai.vectorstore.pgvector.dimensions=768
spring.ai.vectorstore.pgvector.distance-type=COSINE_DISTANCE

# Google GenAI Configuration
spring.ai.google.genai.api-key=${GEMINI_API_KEY}
spring.ai.google.genai.chat.options.model=gemini-2.5-flash
spring.ai.google.genai.chat.options.temperature=0.2
```

### 3. Build & Run
```bash
mvn clean compile
mvn spring-boot:run
```
The API will initialize at `http://localhost:8080`.

---

## 📡 REST API Reference

### 1. Ingest Proprietary Document
```http
POST /documents/ingest
Content-Type: application/json

{
  "filePath": "src/main/resources/documents/MRTT_Leave_Policy.pdf"
}
```

### 2. Execute Baseline Query (Parametric Control)
```http
POST /chat
Content-Type: application/json

{
  "query": "According to the MRTT Leave Policy, what are the maternity leave entitlements, UIF percentages, and payout caps in Rands?",
  "sessionId": "sess-eval-01",
  "mode": "baseline",
  "domain": "HR"
}
```

### 3. Execute Grounded RAG Query (Treatment)
```http
POST /chat
Content-Type: application/json

{
  "query": "According to the MRTT Leave Policy, what are the maternity leave entitlements, UIF percentages, and payout caps in Rands?",
  "sessionId": "sess-eval-01",
  "mode": "rag",
  "domain": "HR"
}
```

### 4. Inspect Conversational Memory
```http
GET /sessions/sess-eval-01
```

### 5. Run LLM-as-a-Judge Evaluation
```http
POST /evaluation/run
Content-Type: application/json

{
  "queryId": "<QUERY_UUID_RETURNED_FROM_CHAT>",
  "groundTruth": "Employees receive 4 consecutive months of unpaid maternity leave with UIF benefit claims ranging between 38% and 60% up to a maximum salary cap of R17,712 per month (daily payout between R155.82 and R246.01)."
}
```

### 6. Export Empirical Evaluation & Paired Metrics
```http
GET /evaluation/comparison?domain=HR
```

---

## 📊 Empirical Evaluation Findings

| Metric | Baseline (Zero-Shot) | Grounded RAG Pipeline | Absolute Delta ($\Delta$) |
| :--- | :---: | :---: | :---: |
| **Faithfulness / Factuality** | $0.10$ | $\mathbf{1.00}$ | $\mathbf{+0.90}$ |
| **Context Precision** | $N/A$ | $\mathbf{1.00}$ | $100\%$ Target Coverage |
| **Answer Relevancy** | $0.65$ | $\mathbf{1.00}$ | $\mathbf{+0.35}$ |
| **Hallucination Rate** | High / Unverified | $\mathbf{0.00}$ | **Complete Mitigation** |

---

## 👤 Author
- **Name**: Thabo Tshabalala
- **Institution**: Cape Peninsula University of Technology (CPUT)
- **Domain**: `za.ac.cput.rag`
