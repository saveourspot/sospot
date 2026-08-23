package org.example.sospot.ai;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "llm.usage")
@Validated
public record AiUsageProperties(
    @Min(1) int perIpRequestsPerMinute,
    @Min(1) int dailyModelCalls,
    @Min(1) int cacheMaximumSize,
    Duration cacheTtl
) {}
