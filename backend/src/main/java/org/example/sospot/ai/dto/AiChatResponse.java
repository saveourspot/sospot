package org.example.sospot.ai.dto;

import java.util.List;

public record AiChatResponse(
    String answer,
    List<ToolCall> toolCalls,
    String mode,
    Integer hops
) {
    public record ToolCall(String name, Object args, Object result) {}

    public static AiChatResponse llm(String answer, List<ToolCall> calls, int hops) {
        return new AiChatResponse(answer, calls, "llm", hops);
    }

    public static AiChatResponse fallback(String answer, List<ToolCall> calls) {
        return new AiChatResponse(answer, calls, "template", null);
    }
}
