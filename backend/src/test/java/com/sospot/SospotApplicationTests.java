package org.example.sospot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.example.sospot.service.AnomalyRegionService;
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
  @Autowired private AnomalyRegionService anomalyRegionService;

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

  @Test
  void anomalySearchSupportsFiltersSortAndLimit() {
    var top = anomalyRegionService.search(null, null, "MAJOR", null, null, "score", 10);

    assertThat(top.period()).isEqualTo("202606");
    assertThat(top.data().items()).hasSize(10);
    assertThat(top.data().items().get(0).dongName()).isEqualTo("판암1동");
    assertThat(top.data().items().get(0).catName()).isEqualTo("과학·기술");
    assertThat(top.data().items().get(0).score()).isEqualByComparingTo("100.000");

    var science = anomalyRegionService.search(null, "M1", "MAJOR", null, null, "score", 100);
    assertThat(science.data().items()).allMatch(item -> item.catCode().equals("M1"));
    assertThat(science.data().items()).anyMatch(item -> item.sampleSizeFlag().equals("LOW"));

    var declining =
        anomalyRegionService.search(null, null, "MAJOR", null, true, "relativeGap", 20);
    assertThat(declining.data().items()).allMatch(item -> item.consecutiveDecline());
  }

  @Test
  void anomalySearchRejectsUnknownSort() {
    assertThatThrownBy(
            () ->
                anomalyRegionService.search(
                    null, null, "MAJOR", null, null, "unknown", 10))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("400 BAD_REQUEST");
  }

}
