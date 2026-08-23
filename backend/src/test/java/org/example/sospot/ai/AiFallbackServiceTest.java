package org.example.sospot.ai;

import org.example.sospot.service.AnalysisPeriodService;
import org.example.sospot.service.AnomalyRegionService;
import org.example.sospot.service.RegionDetailService;
import org.example.sospot.dto.AnomalyRegionsResponse;
import org.example.sospot.dto.ApiEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiFallbackServiceTest {

    private AnalysisPeriodService analysisPeriodService;
    private AnomalyRegionService anomalyRegionService;
    private AiFallbackService service;

    @BeforeEach
    void setUp() throws Exception {
        AliasesLoader aliasesLoader = new AliasesLoader(new ObjectMapper());
        aliasesLoader.load();
        analysisPeriodService = mock(AnalysisPeriodService.class);
        anomalyRegionService = mock(AnomalyRegionService.class);
        service = new AiFallbackService(
            aliasesLoader,
            mock(RegionDetailService.class),
            anomalyRegionService,
            analysisPeriodService
        );
    }

    @Test
    void rejectsRegionsOutsideDaejeonBeforeModelCall() {
        var response = service.guardrailAnswer("부산 음식업 이상징후 알려줘");

        assertThat(response).isPresent();
        assertThat(response.orElseThrow().answer()).contains("대전광역시 82개 행정동만 분석");
    }

    @Test
    void rejectsUnsupportedAnalysisPeriod() {
        when(analysisPeriodService.resolve("202609"))
            .thenThrow(new IllegalArgumentException("지원하지 않는 분석 기간"));

        var response = service.guardrailAnswer("2026.09 목동 분석 결과 알려줘");

        assertThat(response).isPresent();
        assertThat(response.orElseThrow().answer()).contains("분석 결과가 없습니다");
    }

    @Test
    void rejectsRegionalIndustryCrossBsi() {
        var response = service.guardrailAnswer("대전 음식업 BSI 알려줘");

        assertThat(response).isPresent();
        assertThat(response.orElseThrow().answer()).contains("지역과 업종을 교차한 값은 존재하지 않습니다");
    }

    @Test
    void rejectsForecastAndClosureRateQuestions() {
        var response = service.guardrailAnswer("목동 미래 폐업률 예측해줘");

        assertThat(response).isPresent();
        assertThat(response.orElseThrow().answer()).contains("미래 예측, 매출, 유동인구, 개별 점포 폐업");
    }

    @Test
    void explainsWhenBroadNeighborhoodNameMapsToMultipleAdministrativeDongs() {
        var response = service.guardrailAnswer("노은동 음식업 현황 알려줘");

        assertThat(response).isPresent();
        assertThat(response.orElseThrow().answer())
            .contains("단일 행정동이 아닙니다")
            .contains("노은1동·노은2동·노은3동");
    }

    @Test
    void allowsSpecificNumberedAdministrativeDong() {
        var response = service.guardrailAnswer("노은2동 음식업 현황 알려줘");

        assertThat(response).isEmpty();
    }

    @Test
    void fallsBackToPriorityRegionCategoryCombinationsForRecommendedQuestion() {
        var item = new AnomalyRegionsResponse.Item(
            "30110551", "판암1동", "동구", "M1", "과학·기술", "MAJOR",
            12, new java.math.BigDecimal("-0.3333"), new java.math.BigDecimal("0.0823"),
            new java.math.BigDecimal("-0.4156"), new java.math.BigDecimal("-0.4000"),
            true, "OK", new java.math.BigDecimal("100.00"), "중점검토"
        );
        when(anomalyRegionService.search(null, null, "MAJOR", null, null, "score", 5))
            .thenReturn(new ApiEnvelope<>("202606", java.util.List.of(),
                new AnomalyRegionsResponse(java.util.List.of(item))));

        var response = service.answer(
            "이번 분기에 먼저 살펴볼 구역은 어디임?",
            new RuntimeException("LLM unavailable")
        );

        assertThat(response.answer())
            .contains("2026.06 기준 우선 검토 대상 상위 지역·업종 조합")
            .contains("1. 동구 판암1동 × 과학·기술")
            .contains("직전 분기 대비 -33.33%");
        assertThat(response.toolCalls()).extracting(call -> call.name())
            .containsExactly("searchAnomalyRegions");
    }

}
