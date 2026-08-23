package org.example.sospot.ai;

import org.example.sospot.ai.dto.AiChatResponse;
import org.example.sospot.ai.tools.AiTool;
import org.example.sospot.ai.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private final GeminiClient geminiClient;
    private final SystemPromptLoader promptLoader;
    private final ToolRegistry toolRegistry;
    private final AiFallbackService fallbackService;
    private final ObjectMapper mapper;
    private final LlmProperties properties;
    private final AiCostControlService costControlService;

    public AiChatService(
        GeminiClient geminiClient,
        SystemPromptLoader promptLoader,
        ToolRegistry toolRegistry,
        AiFallbackService fallbackService,
        ObjectMapper mapper,
        LlmProperties properties,
        AiCostControlService costControlService
    ) {
        this.geminiClient = geminiClient;
        this.promptLoader = promptLoader;
        this.toolRegistry = toolRegistry;
        this.fallbackService = fallbackService;
        this.mapper = mapper;
        this.properties = properties;
        this.costControlService = costControlService;
    }

    public AiChatResponse chat(String question, String clientKey) {
        AiChatResponse guardrailResponse = fallbackService.guardrailAnswer(question).orElse(null);
        if (guardrailResponse != null) {
            return guardrailResponse;
        }
        AiChatResponse cached = costControlService.getCached(question);
        if (cached != null) {
            return cached;
        }
        if (!costControlService.allowQuestion(clientKey)) {
            return fallbackService.answer(
                question,
                new IllegalStateException("IP별 분당 AI 요청 한도 초과")
            );
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return fallbackService.answer(
                question,
                new IllegalStateException("Gemini API 키 미설정")
            );
        }
        try {
            AiChatResponse response = chatWithLlm(question);
            costControlService.cache(question, response);
            return response;
        } catch (RuntimeException e) {
            log.warn("LLM 경로 실패, fallback으로 전환", e);
            return fallbackService.answer(question, e);
        }
    }

    private AiChatResponse chatWithLlm(String question) {
        List<Map<String, Object>> conversation = new ArrayList<>();
        conversation.add(Map.of(
            "role", "user",
            "parts", List.of(Map.of("text", question))
        ));

        List<AiChatResponse.ToolCall> citations = new ArrayList<>();
        int maxHops = properties.maxToolHops();

        for (int hop = 0; hop <= maxHops; hop++) {
            if (!costControlService.allowModelCall()) {
                throw new IllegalStateException("일일 Gemini 모델 호출 한도 초과");
            }
            JsonNode response = geminiClient.generate(promptLoader.prompt(), conversation, toolRegistry.schemas());
            JsonNode content = response.path("candidates").path(0).path("content");
            JsonNode parts = content.path("parts");
            if (parts.isMissingNode() || parts.isEmpty()) {
                throw new IllegalStateException("Gemini 응답에 parts 없음: " + response);
            }

            JsonNode functionCallPart = firstFunctionCall(parts);
            if (functionCallPart == null) {
                String text = extractText(parts);
                return AiChatResponse.llm(text, citations, hop);
            }

            if (hop == maxHops) {
                throw new IllegalStateException("최대 도구 호출 횟수(" + maxHops + ") 초과");
            }

            String toolName = functionCallPart.path("name").asText();
            Map<String, Object> args = extractArgs(functionCallPart.path("args"));
            AiTool tool = toolRegistry.get(toolName)
                .orElseThrow(() -> new IllegalStateException("알 수 없는 도구: " + toolName));

            Object toolResult = tool.execute(args);
            citations.add(new AiChatResponse.ToolCall(toolName, args, toolResult));

            conversation.add(toMap(content));
            conversation.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of(
                    "functionResponse", Map.of(
                        "name", toolName,
                        "response", Map.of("result", toolResult)
                    )
                ))
            ));
        }

        throw new IllegalStateException("도구 호출 루프 종료 조건 미달");
    }

    private JsonNode firstFunctionCall(JsonNode parts) {
        for (JsonNode part : parts) {
            if (part.has("functionCall")) {
                return part.get("functionCall");
            }
        }
        return null;
    }

    private String extractText(JsonNode parts) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.has("text")) {
                sb.append(part.get("text").asText());
            }
        }
        String result = sb.toString().trim();
        if (result.isEmpty()) {
            throw new IllegalStateException("Gemini 응답에서 텍스트 추출 실패");
        }
        return result;
    }

    private Map<String, Object> extractArgs(JsonNode args) {
        if (args.isMissingNode() || args.isNull()) {
            return Map.of();
        }
        return mapper.convertValue(args, Map.class);
    }

    private Map<String, Object> toMap(JsonNode node) {
        Map<String, Object> converted = mapper.convertValue(node, LinkedHashMap.class);
        return converted;
    }
}
