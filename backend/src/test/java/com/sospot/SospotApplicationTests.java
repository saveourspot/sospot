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
import org.example.sospot.service.CategoryTrendService;
import org.example.sospot.service.RegionDetailService;
import org.example.sospot.service.RegionComparisonService;
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
  @Autowired private CategoryTrendService categoryTrendService;
  @Autowired private RegionDetailService regionDetailService;
  @Autowired private RegionComparisonService regionComparisonService;

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

  @Test
  void regionDetailMatchesPanamAndMokdongReferenceCases() {
    var panam = regionDetailService.getDetail("30110551", null);

    assertThat(panam.period()).isEqualTo("202606");
    assertThat(panam.data().header().dongName()).isEqualTo("판암1동");
    assertThat(panam.data().header().totalDongCount()).isEqualTo(82);
    assertThat(panam.data().topAnomalies().get(0).catName()).isEqualTo("과학·기술");
    assertThat(panam.data().topAnomalies().get(0).storeCounts())
        .extracting(point -> point.count())
        .containsExactly(20, 18, 12);

    var mokdong = regionDetailService.getDetail("30140550", "202606");
    assertThat(mokdong.data().header().rank()).isEqualTo(1);
    assertThat(mokdong.data().topAnomalies().get(0).catName()).isEqualTo("음식");
    assertThat(mokdong.data().topAnomalies().get(0).storeCounts())
        .extracting(point -> point.count())
        .containsExactly(123, 121, 117);
    assertThat(mokdong.data().topAnomalies().get(0).relativeGap())
        .isEqualByComparingTo("-0.06307");
    assertThat(mokdong.data().trend().series()).hasSize(3);
  }

  @Test
  void regionDetailRejectsUnknownDong() {
    assertThatThrownBy(() -> regionDetailService.getDetail("99999999", null))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("404 NOT_FOUND");
  }

  @Test
  void regionComparisonSupportsAllCategoriesAndCategoryFilter() {
    var all = regionComparisonService.compare("30140550", "30200540", null, null);

    assertThat(all.period()).isEqualTo("202606");
    assertThat(all.data().regionA().dongName()).isEqualTo("목동");
    assertThat(all.data().regionB().dongName()).isEqualTo("온천2동");
    assertThat(all.data().regionA().categories()).hasSize(10);
    assertThat(all.data().regionB().categories()).hasSize(10);
    assertThat(all.data().comparison().categories()).hasSize(10);

    var food = regionComparisonService.compare("30140550", "30200540", "202606", "I2");
    assertThat(food.data().regionA().categories()).singleElement()
        .satisfies(metric -> {
          assertThat(metric.catName()).isEqualTo("음식");
          assertThat(metric.storeCounts()).extracting(point -> point.count())
              .containsExactly(123, 121, 117);
          assertThat(metric.relativeGap()).isEqualByComparingTo("-0.06307");
        });
    assertThat(food.data().comparison().categories()).hasSize(1);
  }

  @Test
  void regionComparisonRejectsSameDongAndUnknownCategory() {
    assertThatThrownBy(
            () -> regionComparisonService.compare("30140550", "30140550", null, null))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("400 BAD_REQUEST");
    assertThatThrownBy(
            () -> regionComparisonService.compare("30140550", "30200540", null, "UNKNOWN"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("400 BAD_REQUEST");
  }

  @Test
  void categoryTrendSupportsCityAndDongScopes() {
    var city = categoryTrendService.getTrend("I2", "city", null, null);

    assertThat(city.period()).isEqualTo("202606");
    assertThat(city.comparisonPeriods()).containsExactly("202512", "202603", "202606");
    assertThat(city.data().catName()).isEqualTo("음식");
    assertThat(city.data().series()).hasSize(3);
    assertThat(city.data().series().get(2).growthRate()).isEqualByComparingTo("0.03001512");

    var mokdong =
        categoryTrendService.getTrend("I2", "dong", "30140550", "202512,202606");
    assertThat(mokdong.data().dongCode()).isEqualTo("30140550");
    assertThat(mokdong.data().series()).extracting(point -> point.storeCount())
        .containsExactly(123, 121, 117);
    assertThat(mokdong.data().series().get(2).growthRate()).isEqualByComparingTo("-0.03305785");
  }

  @Test
  void categoryTrendRejectsInvalidScopeAndMissingDong() {
    assertThatThrownBy(() -> categoryTrendService.getTrend("I2", "region", null, null))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("400 BAD_REQUEST");
    assertThatThrownBy(() -> categoryTrendService.getTrend("I2", "dong", null, null))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("400 BAD_REQUEST");
  }

}
