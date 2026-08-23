package org.example.sospot.ai;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "llm.google")
@Validated
public record LlmProperties(
    String apiKey,
    @NotBlank String model,
    @NotBlank String baseUrl,
    @NotNull Duration timeout,
    @Min(1) int maxToolHops
) {}
