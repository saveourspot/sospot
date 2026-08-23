package org.example.sospot.ai;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class SystemPromptLoader {

    private static final String RESOURCE = "ai/system-prompt.md";

    private String prompt;

    @PostConstruct
    void load() throws IOException {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            prompt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public String prompt() {
        return prompt;
    }
}
