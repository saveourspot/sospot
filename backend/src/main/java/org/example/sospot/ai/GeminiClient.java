package org.example.sospot.ai;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class GeminiClient {

    private final RestClient client;
    private final LlmProperties properties;
    private final ObjectMapper mapper;

    public GeminiClient(RestClient geminiRestClient, LlmProperties properties, ObjectMapper mapper) {
        this.client = geminiRestClient;
        this.properties = properties;
        this.mapper = mapper;
    }

    public JsonNode generate(String systemPrompt, List<Map<String, Object>> conversation, List<Map<String, Object>> toolSchemas) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("Gemini API 키가 설정되지 않았습니다.");
        }

        Map<String, Object> body = Map.of(
            "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
            "contents", conversation,
            "tools", List.of(Map.of("functionDeclarations", toolSchemas)),
            "toolConfig", Map.of("functionCallingConfig", Map.of("mode", "AUTO")),
            "generationConfig", Map.of(
                "temperature", 0.2,
                "maxOutputTokens", 2048
            )
        );

        URI endpoint = URI.create(
            properties.baseUrl()
                + "/models/" + properties.model()
                + ":generateContent?key=" + java.net.URLEncoder.encode(properties.apiKey(), StandardCharsets.UTF_8)
        );
        String response = client.post()
            .uri(endpoint)
            .header("Accept", "application/json")
            .body(body)
            .exchange((req, res) -> {
                int status = res.getStatusCode().value();
                byte[] bytes;
                try (var in = res.getBody()) {
                    bytes = in.readAllBytes();
                }
                String text = new String(bytes, StandardCharsets.UTF_8);
                if (status >= 400) {
                    throw new IllegalStateException("Gemini HTTP " + status + " body=" + text);
                }
                return text;
            });

        try {
            return mapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException("Gemini 응답 파싱 실패", e);
        }
    }
}
