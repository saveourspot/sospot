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
import org.example.sospot.service.BsiService;
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
  @Autowired private BsiService bsiService;
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
    assertThat(mokdong.data().majorRelativeGaps())
        .anySatisfy(
            category -> {
              assertThat(category.growthRate()).isNotNull();
              assertThat(category.cityGrowthRate()).isNotNull();
            });
    assertThat(mokdong.data().growthMomentum()).hasSizeLessThanOrEqualTo(3)
        .allSatisfy(
            momentum -> {
              assertThat(momentum.momentumType())
                  .isIn("지속 성장형", "회복 전환형", "상대 우위형", "최근 증가형");
              assertThat(momentum.reviewDirections()).hasSize(3);
              assertThat(momentum.caution()).contains("정책 효과를 단정할 수 없어");
            });
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

  @Test
  void commercialBenchmarksReturnComparableRegionsAndEvidence() {
    var response = regionDetailService.getCommercialBenchmarks("30110551", "202606");

    assertThat(response.period()).isEqualTo("202606");
    assertThat(response.comparisonPeriods()).containsExactly("202512", "202603", "202606");
    assertThat(response.data().benchmarkRegions()).hasSize(2)
        .allSatisfy(
            region -> {
              assertThat(region.dongCode()).isNotEqualTo("30110551");
              assertThat(region.commercialMixSimilarity()).isBetween(
                  new java.math.BigDecimal("0.0"), new java.math.BigDecimal("100.0"));
              assertThat(region.advantageCategoryCount())
                  .isGreaterThanOrEqualTo(region.advantageCategories().size());
              assertThat(region.advantageCategories()).hasSizeBetween(1, 2)
                  .allSatisfy(
                      category -> {
                        assertThat(category.relativeGapDifference()).isPositive();
                        assertThat(category.benchmarkRelativeGap())
                            .isGreaterThan(category.targetRelativeGap());
                        assertThat(category.applicationDirections()).hasSize(3)
                            .allMatch(direction -> !direction.contains("성공 사례"));
                      });
            });
  }

  @Test
  void selectedCategoryScoresExcludeLowSamplesAndRankDongs() {
    var result = anomalyRegionService.selectedScores("202606", "I2,G2");

    assertThat(result.period()).isEqualTo("202606");
    assertThat(result.data().items()).hasSize(82);
    assertThat(result.data().items())
        .allSatisfy(
            item -> {
              assertThat(item.selectedCategoryCount()).isEqualTo(2);
              assertThat(item.validCategoryCount()).isBetween(0, 2);
              if (item.validCategoryCount() == 0) {
                assertThat(item.sampleSizeFlag()).isEqualTo("LOW");
                assertThat(item.score()).isNull();
                assertThat(item.grade()).isNull();
              } else {
                assertThat(item.sampleSizeFlag()).isEqualTo("OK");
                assertThat(item.rawScore()).isNotNull();
                assertThat(item.score()).isNotNull();
                assertThat(item.grade()).isIn("정상", "관심", "주의", "중점검토");
              }
            });
  }

  @Test
  void selectingAllMajorCategoriesMatchesStoredDongScores() {
    var selected = anomalyRegionService.selectedScores("202606", "G2,I1,I2,L1,M1,N1,P1,Q1,R1,S2");
    var stored = regionScoreService.getScores("202606");

    assertThat(selected.data().items()).hasSize(82);
    assertThat(selected.data().items())
        .allSatisfy(
            item -> {
              var storedItem =
                  stored.data().items().stream()
                      .filter(candidate -> candidate.dongCode().equals(item.dongCode()))
                      .findFirst()
                      .orElseThrow();
              assertThat(item.score()).isEqualByComparingTo(storedItem.pctScore());
              assertThat(item.rawScore()).isEqualByComparingTo(storedItem.rawScore());
              assertThat(item.grade()).isEqualTo(storedItem.grade());
            });
  }

  @Test
  void bsiReturnsLatestMonthlyContextAndQuarterlyDaejeonSeries() {
    var latest = bsiService.getBsi(null);

    assertThat(latest.period()).isEqualTo("202606");
    assertThat(latest.data().periodMonth()).isEqualTo("2026-05");
    assertThat(latest.data().metrics().overallSentiment()).isEqualByComparingTo("67.90");
    assertThat(latest.data().metrics().daejeonSentiment()).isEqualByComparingTo("63.60");
    assertThat(latest.data().metrics().industrySentiment())
        .containsEntry("음식점업체감", new java.math.BigDecimal("68.00"))
        .hasSize(9);
    assertThat(latest.data().quarterlySeries()).extracting(point -> point.value())
        .containsExactly(
            new java.math.BigDecimal("72.67"),
            new java.math.BigDecimal("65.20"),
            new java.math.BigDecimal("60.00"));

    var april = bsiService.getBsi("2026-04");
    assertThat(april.data().metrics().overallSentiment()).isEqualByComparingTo("63.70");
    assertThat(april.data().metrics().daejeonSentiment()).isEqualByComparingTo("56.40");
  }

  @Test
  void bsiRejectsInvalidOrFutureMonth() {
    assertThatThrownBy(() -> bsiService.getBsi("202605"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("400 BAD_REQUEST");
    assertThatThrownBy(() -> bsiService.getBsi("2026-07"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("400 BAD_REQUEST");
  }

}
