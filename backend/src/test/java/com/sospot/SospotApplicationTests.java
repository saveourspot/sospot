package org.example.sospot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.example.sospot.repository.AnomalyRepository;
import org.example.sospot.repository.BsiRepository;
import org.example.sospot.repository.CategoryRepository;
import org.example.sospot.repository.DongRepository;
import org.example.sospot.repository.DongScoreRepository;
import org.example.sospot.repository.PeriodRepository;
import org.example.sospot.repository.StoreCountRepository;
import org.example.sospot.service.RegionScoreService;
import org.example.sospot.service.SummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SospotApplicationTests {

  @Autowired private PeriodRepository periodRepository;
  @Autowired private DongRepository dongRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private StoreCountRepository storeCountRepository;
  @Autowired private AnomalyRepository anomalyRepository;
  @Autowired private DongScoreRepository dongScoreRepository;
  @Autowired private BsiRepository bsiRepository;
  @Autowired private SummaryService summaryService;
  @Autowired private RegionScoreService regionScoreService;

  @Test
  void contextLoads() {
    assertThat(periodRepository.count()).isEqualTo(3);
    assertThat(dongRepository.count()).isEqualTo(82);
    assertThat(categoryRepository.findByCatLevelOrderByCatCodeAsc("MAJOR")).hasSize(10);
    assertThat(storeCountRepository.count()).isPositive();
    assertThat(anomalyRepository.count()).isEqualTo(5_817);
    assertThat(dongScoreRepository.findLatestAnalyzedPeriodId()).contains("202606");
    assertThat(dongScoreRepository.findByPeriodIdOrderByPctScoreDesc("202606")).hasSize(82);
    assertThat(bsiRepository.count()).isPositive();
  }

  @Test
  void summaryUsesLatestCompletedAnalysisPeriod() {
    var response = summaryService.getSummary(null);

    assertThat(response.period()).isEqualTo("202606");
    assertThat(response.comparisonPeriods()).containsExactly("202512", "202603", "202606");
    assertThat(response.data().analyzedDongCount()).isEqualTo(82);
    assertThat(response.data().gradeCounts())
        .containsEntry("중점검토", 9L)
        .containsEntry("주의", 16L)
        .containsEntry("관심", 25L)
        .containsEntry("정상", 32L);
    assertThat(response.data().cityStoreGrowthRate()).isEqualByComparingTo("0.02667701");
    assertThat(response.data().latestBsi().periodMonth()).isEqualTo("2026-05");
    assertThat(response.data().latestBsi().value()).isEqualByComparingTo("63.60");
    assertThat(response.data().topRegions()).hasSize(5);
  }

  @Test
  void regionScoresReturnsAllDongsOrderedByPercentile() {
    var response = regionScoreService.getScores(null);

    assertThat(response.period()).isEqualTo("202606");
    assertThat(response.data().items()).hasSize(82);
    assertThat(response.data().items().get(0).pctScore()).isEqualByComparingTo("100.000");
  }

}
