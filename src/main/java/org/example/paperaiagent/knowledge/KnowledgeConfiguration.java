package org.example.paperaiagent.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "paper.knowledge.enabled", havingValue = "true")
public class KnowledgeConfiguration {

    @Bean
    public CloudKnowledgeSearchClient cloudKnowledgeSearchClient(
            @Value("${paper.knowledge.endpoint}") String endpoint,
            @Value("${paper.knowledge.api-key}") String apiKey,
            @Value("${paper.knowledge.index-id}") String indexId,
            @Value("${paper.knowledge.top-k:5}") int topK,
            @Value("${paper.knowledge.connect-timeout:PT10S}") String connectTimeout,
            @Value("${paper.knowledge.read-timeout:PT60S}") String readTimeout,
            ObjectMapper objectMapper
    ) {
        CloudKnowledgeSearchClient.Settings settings = new CloudKnowledgeSearchClient.Settings(
                URI.create(endpoint),
                apiKey,
                indexId,
                topK,
                Duration.parse(connectTimeout),
                Duration.parse(readTimeout)
        );
        return new CloudKnowledgeSearchClient(settings, objectMapper);
    }

    @Bean
    public KnowledgeToolAdapter knowledgeToolAdapter(CloudKnowledgeSearchClient client) {
        return new KnowledgeToolAdapter(client);
    }
}
