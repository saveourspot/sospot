package org.example.sospot.ai.tools;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private final Map<String, AiTool> byName;

    public ToolRegistry(List<AiTool> tools) {
        this.byName = tools.stream().collect(Collectors.toUnmodifiableMap(AiTool::name, t -> t));
    }

    public Optional<AiTool> get(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public List<Map<String, Object>> schemas() {
        return byName.values().stream().map(AiTool::schema).toList();
    }

    public java.util.Set<String> names() {
        return byName.keySet();
    }
}
