package com.ramteja.calls.llm;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Calls any OpenAI-compatible Chat Completions endpoint (OpenAI, Azure OpenAI, Groq, Ollama, vLLM).
 * Uses JSON mode and temperature 0 for consistent, parseable grading.
 */
@Component
public class OpenAiCompatibleClient implements LlmClient {

    private final RestClient http;
    private final String model;

    public OpenAiCompatibleClient(@Value("${llm.base-url}") String baseUrl,
                                  @Value("${llm.api-key:}") String apiKey,
                                  @Value("${llm.model}") String model,
                                  @Value("${llm.timeout-ms:30000}") int timeoutMs) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(timeoutMs);
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.model = model;
    }

    @Override
    public String completeJson(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));
        try {
            JsonNode response = http.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new LlmException("LLM provider returned HTTP " + res.getStatusCode().value());
                    })
                    .body(JsonNode.class);
            JsonNode content = response == null ? null : response.at("/choices/0/message/content");
            if (content == null || content.isMissingNode() || content.asText().isBlank()) {
                throw new LlmException("LLM response had no content");
            }
            return content.asText();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM call failed: " + e.getMessage(), e);
        }
    }
}
