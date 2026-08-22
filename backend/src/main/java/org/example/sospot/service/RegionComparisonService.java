package org.example.sospot.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.example.sospot.domain.Anomaly;
import org.example.sospot.domain.Category;
import org.example.sospot.domain.Dong;
import org.example.sospot.domain.DongScore;
import org.example.sospot.domain.StoreCount;
import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.dto.RegionComparisonResponse;
import org.example.sospot.dto.RegionDetailResponse;
import org.example.sospot.repository.AnomalyRepository;
import org.example.sospot.repository.CategoryRepository;
import org.example.sospot.repository.DongRepository;
import org.example.sospot.repository.DongScoreRepository;
import org.example.sospot.repository.StoreCountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegionComparisonService {

  private static final String MAJOR = "MAJOR";

  private final AnalysisPeriodService analysisPeriodService;
  private final DongRepository dongRepository;
  private final DongScoreRepository dongScoreRepository;
  private final AnomalyRepository anomalyRepository;
  private final CategoryRepository categoryRepository;
  private final StoreCountRepository storeCountRepository;

  public RegionComparisonService(
      AnalysisPeriodService analysisPeriodService,
      DongRepository dongRepository,
      DongScoreRepository dongScoreRepository,
      AnomalyRepository anomalyRepository,
      CategoryRepository categoryRepository,
      StoreCountRepository storeCountRepository) {
    this.analysisPeriodService = analysisPeriodService;
    this.dongRepository = dongRepository;
    this.dongScoreRepository = dongScoreRepository;
    this.anomalyRepository = anomalyRepository;
    this.categoryRepository = categoryRepository;
    this.storeCountRepository = storeCountRepository;
  }

  public ApiEnvelope<RegionComparisonResponse> compare(
      String dongA, String dongB, String requestedPeriod, String catCode) {
    if (dongA.equals(dongB)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "서로 다른 두 행정동을 지정해야 합니다.");
    }

    String period = analysisPeriodService.resolve(requestedPeriod);
    List<String> comparisonPeriods = analysisPeriodService.comparisonPeriods(period);
    Map<String, Category> categories = loadCategories(catCode);
    List<DongScore> rankedScores =
        dongScoreRepository.findByPeriodIdOrderByPctScoreDesc(period);

    RegionComparisonResponse.Region regionA =
        buildRegion(dongA, period, comparisonPeriods, categories, rankedScores);
    RegionComparisonResponse.Region regionB =
        buildRegion(dongB, period, comparisonPeriods, categories, rankedScores);

    Map<String, RegionComparisonResponse.CategoryMetric> metricsA =
        regionA.categories().stream()
            .collect(Collectors.toMap(RegionComparisonResponse.CategoryMetric::catCode, Function.identity()));
    Map<String, RegionComparisonResponse.CategoryMetric> metricsB =
        regionB.categories().stream()
            .collect(Collectors.toMap(RegionComparisonResponse.CategoryMetric::catCode, Function.identity()));
    List<RegionComparisonResponse.CategoryDifference> differences =
        categories.values().stream()
            .sorted(Comparator.comparing(Category::getCatCode))
            .map(category -> difference(category, metricsA.get(category.getCatCode()), metricsB.get(category.getCatCode()), dongA, dongB))
            .toList();

    String higherPriority = regionA.rank() <= regionB.rank() ? dongA : dongB;
    RegionComparisonResponse.Comparison comparison =
        new RegionComparisonResponse.Comparison(
            higherPriority, regionA.pctScore().subtract(regionB.pctScore()), differences);
    return new ApiEnvelope<>(
        period, comparisonPeriods, new RegionComparisonResponse(regionA, regionB, comparison));
  }

  private Map<String, Category> loadCategories(String catCode) {
    List<Category> majorCategories = categoryRepository.findByCatLevelOrderByCatCodeAsc(MAJOR);
    if (catCode == null || catCode.isBlank()) {
      return majorCategories.stream()
          .collect(Collectors.toMap(Category::getCatCode, Function.identity()));
    }
    return majorCategories.stream()
        .filter(category -> category.getCatCode().equals(catCode))
        .findFirst()
        .map(category -> Map.of(category.getCatCode(), category))
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하는 대분류 업종이 아닙니다."));
  }

  private RegionComparisonResponse.Region buildRegion(
      String dongCode,
      String period,
      List<String> comparisonPeriods,
      Map<String, Category> categories,
      List<DongScore> rankedScores) {
    Dong dong =
        dongRepository
            .findById(dongCode)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "지원하는 대전 행정동을 찾을 수 없습니다."));
    DongScore score =
        dongScoreRepository
            .findByDongCodeAndPeriodId(dongCode, period)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 기간의 행정동 분석 결과가 없습니다."));
    Map<String, Anomaly> anomalies =
        anomalyRepository
            .findByDongCodeAndPeriodIdAndCatLevelOrderByScoreDesc(dongCode, period, MAJOR)
            .stream()
            .collect(Collectors.toMap(Anomaly::getCatCode, Function.identity()));
    List<RegionComparisonResponse.CategoryMetric> metrics =
        categories.values().stream()
            .sorted(Comparator.comparing(Category::getCatCode))
            .map(
                category ->
                    toMetric(
                        anomalies.get(category.getCatCode()),
                        category,
                        comparisonPeriods,
                        dongCode))
            .toList();
    return new RegionComparisonResponse.Region(
        dong.getDongCode(),
        dong.getDongName(),
        dong.getSigungu(),
        score.getGrade(),
        score.getPctScore(),
        findRank(rankedScores, dongCode),
        score.getAnomalyCatCount(),
        score.getValidCatCount(),
        metrics);
  }

  private RegionComparisonResponse.CategoryMetric toMetric(
      Anomaly anomaly, Category category, List<String> periods, String dongCode) {
    String catCode = category.getCatCode();
    Map<String, Integer> counts =
        storeCountRepository
            .findByDongCodeAndCatCodeOrderByPeriodIdAsc(dongCode, catCode)
            .stream()
            .collect(Collectors.toMap(StoreCount::getPeriodId, StoreCount::getStoreCount));
    List<RegionDetailResponse.StoreCountPoint> storeCounts =
        periods.stream()
            .map(period -> new RegionDetailResponse.StoreCountPoint(period, counts.getOrDefault(period, 0)))
            .toList();
    return new RegionComparisonResponse.CategoryMetric(
        catCode,
        category.getCatName(),
        anomaly == null ? "NO_DATA" : anomaly.getSampleSizeFlag(),
        anomaly == null ? null : anomaly.getScore(),
        anomaly == null ? null : anomaly.getGrade(),
        storeCounts,
        anomaly == null ? null : anomaly.getGrowthRate(),
        anomaly == null ? null : anomaly.getCityGrowthRate(),
        anomaly == null ? null : anomaly.getRelativeGap(),
        anomaly == null ? null : anomaly.getCumChangeRate(),
        anomaly == null ? null : anomaly.getConsecutiveDecline());
  }

  private RegionComparisonResponse.CategoryDifference difference(
      Category category,
      RegionComparisonResponse.CategoryMetric metricA,
      RegionComparisonResponse.CategoryMetric metricB,
      String dongA,
      String dongB) {
    BigDecimal scoreGap = subtract(metricA.score(), metricB.score());
    BigDecimal relativeGapGap = subtract(metricA.relativeGap(), metricB.relativeGap());
    String moreNegative = null;
    if (metricA.relativeGap() != null && metricB.relativeGap() != null) {
      moreNegative = metricA.relativeGap().compareTo(metricB.relativeGap()) <= 0 ? dongA : dongB;
    }
    return new RegionComparisonResponse.CategoryDifference(
        category.getCatCode(), category.getCatName(), scoreGap, relativeGapGap, moreNegative);
  }

  private BigDecimal subtract(BigDecimal left, BigDecimal right) {
    return left == null || right == null ? null : left.subtract(right);
  }

  private int findRank(List<DongScore> rankedScores, String dongCode) {
    for (int index = 0; index < rankedScores.size(); index++) {
      if (rankedScores.get(index).getDongCode().equals(dongCode)) {
        return index + 1;
      }
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "행정동 순위를 찾을 수 없습니다.");
  }
}
