package org.example.sospot.ai;

import org.example.sospot.ai.dto.AiChatResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiCostControlServiceTest {

    @Test
    void limitsQuestionsPerClientWithinOneMinute() {
        AiCostControlService service = service(2, 10);

        assertThat(service.allowQuestion("client-a")).isTrue();
        assertThat(service.allowQuestion("client-a")).isTrue();
        assertThat(service.allowQuestion("client-a")).isFalse();
        assertThat(service.allowQuestion("client-b")).isTrue();
    }

    @Test
    void limitsActualModelCallsAcrossClients() {
        AiCostControlService service = service(10, 2);

        assertThat(service.allowModelCall()).isTrue();
        assertThat(service.allowModelCall()).isTrue();
        assertThat(service.allowModelCall()).isFalse();
    }

    @Test
    void temporarilyBlocksModelCallsAfterProviderQuotaFailure() {
        AiCostControlService service = service(10, 10);

        service.blockModelCalls(Duration.ofMinutes(1));

        assertThat(service.allowModelCall()).isFalse();
    }

    @Test
    void cachesIdenticalNormalizedQuestions() {
        AiCostControlService service = service(10, 10);
        AiChatResponse response = AiChatResponse.llm("답변", List.of(), 0);

        service.cache("  목동   음식업 알려줘 ", response);

        assertThat(service.getCached("목동 음식업 알려줘")).isSameAs(response);
    }

    private AiCostControlService service(int perMinute, int dailyCalls) {
        return new AiCostControlService(
            new AiUsageProperties(perMinute, dailyCalls, 10, Duration.ofMinutes(5))
        );
    }
}
