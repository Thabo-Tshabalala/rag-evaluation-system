package za.ac.cput.rag.memory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Non-destructive configuration for Conversational Chat Memory and deterministic (temperature = 0.0)
 * ChatClient instances for the Reliability Enhancement Framework.
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    @Primary
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    /**
     * Dedicated ChatClient with MessageChatMemoryAdvisor and deterministic temperature (0.0).
     * Uses builder.clone() so existing ChatClient beans (e.g., in RagController) are unaffected.
     */
    @Bean("conversationalChatClient")
    public ChatClient conversationalChatClient(
            ChatClient.Builder chatClientBuilder,
            MessageChatMemoryAdvisor messageChatMemoryAdvisor) {
        return chatClientBuilder.clone()
                .defaultAdvisors(messageChatMemoryAdvisor)
                .defaultOptions(ChatOptions.builder().temperature(0.0).maxTokens(1024))
                .build();
    }

    /**
     * Dedicated standalone ChatClient for LLM-as-a-Judge evaluation at temperature = 0.0
     * without conversational memory advisors.
     */
    @Bean("judgeChatClient")
    public ChatClient judgeChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.clone()
                .defaultOptions(ChatOptions.builder().temperature(0.0).maxTokens(1024))
                .build();
    }
}
