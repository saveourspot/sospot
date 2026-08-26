package org.example.sospot.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.example.sospot.domain.Category;
import org.example.sospot.domain.Dong;
import org.example.sospot.dto.AnomalyRegionsResponse;
import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.dto.SelectedCategoryScoresResponse;
import org.example.sospot.repository.AnomalyRepository;
import org.example.sospot.repository.CategoryRepository;
import org.example.sospot.repository.DongRepository;
import org.example.sospot.repository.DongScoreRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnomalyRegionService {

  private static final Set<String> CAT_LEVELS = Set.of("MAJOR", "MIDDLE");
  private static final Set<String> GRADES = Set.of("정상", "관심", "주의", "중점검토");
  private static final int DEFAULT_TOP_N = 100;
  private static final int MAX_TOP_N = 500;
  private static final String MAJOR = "MAJOR";

  private final AnalysisPeriodService analysisPeriodService;
  private final AnomalyRepository anomalyRepository;
  private final DongRepository dongRepository;
  private final CategoryRepository categoryRepository;
  private final DongScoreRepository dongScoreRepository;

  public AnomalyRegionService(
      AnalysisPeriodService analysisPeriodService,
      AnomalyRepository anomalyRepository,
      DongRepository dongRepository,
      CategoryRepository categoryRepository,
      DongScoreRepository dongScoreRepository) {
    this.analysisPeriodService = analysisPeriodService;
    this.anomalyRepository = anomalyRepository;
    this.dongRepository = dongRepository;
    this.categoryRepository = categoryRepository;
    this.dongScoreRepository = dongScoreRepository;
  }

  public ApiEnvelope<AnomalyRegionsResponse> search(
      String requestedPeriod,
      String catCode,
      String requestedCatLevel,
      String grade,
      Boolean consecutiveDecline,
      String requestedSortBy,
      Integer requestedTopN) {
    String period = analysisPeriodService.resolve(requestedPeriod);
    String catLevel = normalizeCatLevel(requestedCatLevel);
    String normalizedCatCode = normalizeOptional(catCode);
    String normalizedGrade = normalizeGrade(grade);
    int topN = normalizeTopN(requestedTopN);
    Sort sort = resolveSort(requestedSortBy);

    if (normalizedCatCode != null) {
      Category category =
          categoryRepository
              .findById(normalizedCatCode)
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.BAD_REQUEST, "지원하지 않는 업종 코드입니다."));
      if (!catLevel.equals(category.getCatLevel())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "catCode와 catLevel이 일치하지 않습니다.");
      }
    }

    Map<String, Dong> dongByCode =
        dongRepository.findAll().stream()
            .collect(Collectors.toMap(Dong::getDongCode, Function.identity()));
    Map<String, Category> categoryByCode =
        categoryRepository.findByCatLevelOrderByCatCodeAsc(catLevel).stream()
            .collect(Collectors.toMap(Category::getCatCode, Function.identity()));

    List<AnomalyRegionsResponse.Item> items =
        anomalyRepository
            .search(
                period,
                catLevel,
                normalizedCatCode,
                normalizedGrade,
                consecutiveDecline,
                PageRequest.of(0, topN, sort))
            .stream()
            .map(
                anomaly -> {
                  Dong dong = dongByCode.get(anomaly.getDongCode());
                  Category category = categoryByCode.get(anomaly.getCatCode());
                  return new AnomalyRegionsResponse.Item(
                      anomaly.getDongCode(),
                      dong.getDongName(),
                      dong.getSigungu(),
                      anomaly.getCatCode(),
                      category.getCatName(),
                      anomaly.getCatLevel(),
                      anomaly.getStoreCount(),
                      anomaly.getGrowthRate(),
                      anomaly.getCityGrowthRate(),
                      anomaly.getRelativeGap(),
                      anomaly.getCumChangeRate(),
                      anomaly.getConsecutiveDecline(),
                      anomaly.getSampleSizeFlag(),
                      anomaly.getScore(),
                      anomaly.getGrade());
                })
            .toList();

    return new ApiEnvelope<>(
        period,
        analysisPeriodService.comparisonPeriods(period),
        new AnomalyRegionsResponse(items));
  }

  public ApiEnvelope<SelectedCategoryScoresResponse> selectedScores(
      String requestedPeriod, String requestedCatCodes) {
    String period = analysisPeriodService.resolve(requestedPeriod);
    Set<String> catCodes = parseMajorCategoryCodes(requestedCatCodes);
    Set<String> allMajorCodes =
        categoryRepository.findByCatLevelOrderByCatCodeAsc(MAJOR).stream()
            .map(Category::getCatCode)
            .collect(Collectors.toSet());
    Map<String, Dong> dongByCode =
        dongRepository.findAll().stream()
            .collect(Collectors.toMap(Dong::getDongCode, Function.identity()));

    if (catCodes.equals(allMajorCodes)) {
      List<SelectedCategoryScoresResponse.Item> items =
          dongScoreRepository.findByPeriodIdOrderByPctScoreDesc(period).stream()
              .map(
                  score -> {
                    Dong dong = dongByCode.get(score.getDongCode());
                    return new SelectedCategoryScoresResponse.Item(
                        score.getDongCode(),
                        dong.getDongName(),
                        dong.getSigungu(),
                        catCodes.size(),
                        score.getValidCatCount(),
                        score.getAnomalyCatCount(),
                        score.getValidCatCount() == 0 ? "LOW" : "OK",
                        score.getRawScore(),
                        score.getPctScore(),
                        score.getGrade());
                  })
              .toList();
      return selectedScoresEnvelope(period, items);
    }

    Map<String, List<org.example.sospot.domain.Anomaly>> anomaliesByDong =
        anomalyRepository.findByPeriodIdAndCatLevelAndCatCodeIn(period, MAJOR, catCodes).stream()
            .collect(Collectors.groupingBy(org.example.sospot.domain.Anomaly::getDongCode));

    List<SelectedDongRawScore> rawScores =
        dongByCode.values().stream()
            .map(
                dong -> {
                  List<BigDecimal> validScores =
                      anomaliesByDong.getOrDefault(dong.getDongCode(), List.of()).stream()
                          .filter(anomaly -> !"LOW".equals(anomaly.getSampleSizeFlag()))
                          .map(org.example.sospot.domain.Anomaly::getScore)
                          .filter(java.util.Objects::nonNull)
                          .toList();
                  int anomalyCategoryCount =
                      (int)
                          validScores.stream()
                              .filter(score -> score.compareTo(BigDecimal.valueOf(50)) >= 0)
                              .count();
                  BigDecimal topAverage =
                      average(
                          validScores.stream()
                              .sorted(Comparator.reverseOrder())
                              .limit(3)
                              .toList());
                  BigDecimal weight =
                      BigDecimal.valueOf(Math.min(1.0, 0.6 + 0.1 * anomalyCategoryCount));
                  BigDecimal rawScore = topAverage == null ? null : topAverage.multiply(weight);
                  return new SelectedDongRawScore(
                      dong, validScores.size(), anomalyCategoryCount, rawScore);
                })
            .toList();

    List<SelectedDongRawScore> ranked =
        rawScores.stream().filter(item -> item.rawScore() != null).toList();
    List<SelectedCategoryScoresResponse.Item> items =
        rawScores.stream()
            .map(item -> selectedScoreItem(item, catCodes.size(), ranked))
            .sorted(
                Comparator.comparing(
                        SelectedCategoryScoresResponse.Item::score,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(SelectedCategoryScoresResponse.Item::dongCode))
            .toList();

    return selectedScoresEnvelope(period, items);
  }

  private SelectedCategoryScoresResponse.Item selectedScoreItem(
      SelectedDongRawScore item, int selectedCategoryCount, List<SelectedDongRawScore> ranked) {
    BigDecimal percentile = percentile(item.rawScore(), ranked);
    Dong dong = item.dong();
    return new SelectedCategoryScoresResponse.Item(
        dong.getDongCode(),
        dong.getDongName(),
        dong.getSigungu(),
        selectedCategoryCount,
        item.validCategoryCount(),
        item.anomalyCategoryCount(),
        item.rawScore() == null ? "LOW" : "OK",
        rounded(item.rawScore()),
        percentile,
        gradeForPercentile(percentile));
  }

  private BigDecimal percentile(BigDecimal rawScore, List<SelectedDongRawScore> ranked) {
    if (rawScore == null || ranked.isEmpty()) return null;
    long lower = ranked.stream().filter(item -> item.rawScore().compareTo(rawScore) < 0).count();
    long equal = ranked.stream().filter(item -> item.rawScore().compareTo(rawScore) == 0).count();
    BigDecimal averageRank = BigDecimal.valueOf(lower * 2 + equal + 1).divide(BigDecimal.valueOf(2));
    return averageRank
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(ranked.size()), 3, RoundingMode.HALF_UP);
  }

  private ApiEnvelope<SelectedCategoryScoresResponse> selectedScoresEnvelope(
      String period, List<SelectedCategoryScoresResponse.Item> items) {
    return new ApiEnvelope<>(
        period,
        analysisPeriodService.comparisonPeriods(period),
        new SelectedCategoryScoresResponse(items));
  }

  private Set<String> parseMajorCategoryCodes(String requestedCatCodes) {
    if (requestedCatCodes == null || requestedCatCodes.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "catCodes를 하나 이상 지정해야 합니다.");
    }
    Set<String> catCodes =
        java.util.Arrays.stream(requestedCatCodes.split(","))
            .map(String::trim)
            .filter(code -> !code.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<String> supportedCodes =
        categoryRepository.findByCatLevelOrderByCatCodeAsc(MAJOR).stream()
            .map(Category::getCatCode)
            .collect(Collectors.toSet());
    if (catCodes.isEmpty() || !supportedCodes.containsAll(catCodes)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 대분류 업종 코드가 포함되어 있습니다.");
    }
    return catCodes;
  }

  private BigDecimal average(List<BigDecimal> scores) {
    if (scores.isEmpty()) return null;
    return scores.stream()
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(scores.size()), 3, RoundingMode.HALF_UP);
  }

  private BigDecimal rounded(BigDecimal value) {
    return value == null ? null : value.setScale(3, RoundingMode.HALF_UP);
  }

  private String gradeForPercentile(BigDecimal score) {
    if (score == null) return null;
    if (score.compareTo(BigDecimal.valueOf(90)) >= 0) return "중점검토";
    if (score.compareTo(BigDecimal.valueOf(70)) >= 0) return "주의";
    if (score.compareTo(BigDecimal.valueOf(40)) >= 0) return "관심";
    return "정상";
  }

  private record SelectedDongRawScore(
      Dong dong, int validCategoryCount, int anomalyCategoryCount, BigDecimal rawScore) {}

  private String normalizeCatLevel(String catLevel) {
    String normalized =
        catLevel == null || catLevel.isBlank() ? "MAJOR" : catLevel.trim().toUpperCase();
    if (!CAT_LEVELS.contains(normalized)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "catLevel은 MAJOR 또는 MIDDLE이어야 합니다.");
    }
    return normalized;
  }

  private String normalizeGrade(String grade) {
    String normalized = normalizeOptional(grade);
    if (normalized != null && !GRADES.contains(normalized)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 등급입니다.");
    }
    return normalized;
  }

  private int normalizeTopN(Integer topN) {
    int normalized = topN == null ? DEFAULT_TOP_N : topN;
    if (normalized < 1 || normalized > MAX_TOP_N) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "topN은 1 이상 500 이하여야 합니다.");
    }
    return normalized;
  }

  private Sort resolveSort(String sortBy) {
    String normalized = sortBy == null || sortBy.isBlank() ? "score" : sortBy.trim();
    return switch (normalized) {
      case "score" -> Sort.by(Sort.Order.desc("score").nullsLast());
      case "relativeGap" -> Sort.by(Sort.Order.asc("relativeGap").nullsLast());
      case "cumChange" -> Sort.by(Sort.Order.asc("cumChangeRate").nullsLast());
      default ->
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "sortBy는 score, relativeGap, cumChange 중 하나여야 합니다.");
    };
  }

  private String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
