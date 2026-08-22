package org.example.sospot.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.example.sospot.domain.Category;
import org.example.sospot.domain.Dong;
import org.example.sospot.dto.AnomalyRegionsResponse;
import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.repository.AnomalyRepository;
import org.example.sospot.repository.CategoryRepository;
import org.example.sospot.repository.DongRepository;
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

  private final AnalysisPeriodService analysisPeriodService;
  private final AnomalyRepository anomalyRepository;
  private final DongRepository dongRepository;
  private final CategoryRepository categoryRepository;

  public AnomalyRegionService(
      AnalysisPeriodService analysisPeriodService,
      AnomalyRepository anomalyRepository,
      DongRepository dongRepository,
      CategoryRepository categoryRepository) {
    this.analysisPeriodService = analysisPeriodService;
    this.anomalyRepository = anomalyRepository;
    this.dongRepository = dongRepository;
    this.categoryRepository = categoryRepository;
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
