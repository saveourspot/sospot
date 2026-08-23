package org.example.sospot.ai;

import org.example.sospot.service.AnalysisPeriodService;
import org.example.sospot.service.AnomalyRegionService;
import org.example.sospot.service.RegionDetailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiFallbackServiceTest {

    private AnalysisPeriodService analysisPeriodService;
    private AiFallbackService service;

    @BeforeEach
    void setUp() throws Exception {
        AliasesLoader aliasesLoader = new AliasesLoader(new ObjectMapper());
        aliasesLoader.load();
        analysisPeriodService = mock(AnalysisPeriodService.class);
        service = new AiFallbackService(
            aliasesLoader,
            mock(RegionDetailService.class),
            mock(AnomalyRegionService.class),
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
}
