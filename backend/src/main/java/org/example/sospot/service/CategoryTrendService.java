package org.example.sospot.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.example.sospot.domain.Category;
import org.example.sospot.domain.StoreCount;
import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.dto.CategoryTrendResponse;
import org.example.sospot.repository.CategoryRepository;
import org.example.sospot.repository.DongRepository;
import org.example.sospot.repository.PeriodRepository;
import org.example.sospot.repository.StoreCountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CategoryTrendService {

  private static final String CITY = "city";
  private static final String DONG = "dong";

  private final AnalysisPeriodService analysisPeriodService;
  private final CategoryRepository categoryRepository;
  private final DongRepository dongRepository;
  private final PeriodRepository periodRepository;
  private final StoreCountRepository storeCountRepository;

  public CategoryTrendService(
      AnalysisPeriodService analysisPeriodService,
      CategoryRepository categoryRepository,
      DongRepository dongRepository,
      PeriodRepository periodRepository,
      StoreCountRepository storeCountRepository) {
    this.analysisPeriodService = analysisPeriodService;
    this.categoryRepository = categoryRepository;
    this.dongRepository = dongRepository;
    this.periodRepository = periodRepository;
    this.storeCountRepository = storeCountRepository;
  }

  public ApiEnvelope<CategoryTrendResponse> getTrend(
      String catCode, String scope, String dongCode, String periodRange) {
    String normalizedScope = scope == null || scope.isBlank() ? CITY : scope.toLowerCase();
    if (!CITY.equals(normalizedScope) && !DONG.equals(normalizedScope)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope는 city 또는 dong이어야 합니다.");
    }
    if (DONG.equals(normalizedScope)) {
      if (dongCode == null || dongCode.isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope=dong이면 dongCode가 필요합니다.");
      }
      if (!dongRepository.existsById(dongCode)) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "지원하는 대전 행정동을 찾을 수 없습니다.");
      }
    }

    Category category =
        categoryRepository
            .findById(catCode)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "지원하는 업종을 찾을 수 없습니다."));
    List<String> periods = resolvePeriods(periodRange);
    List<Integer> counts =
        CITY.equals(normalizedScope)
            ? cityCounts(catCode, periods)
            : dongCounts(dongCode, catCode, periods);
    List<CategoryTrendResponse.TrendPoint> series =
        java.util.stream.IntStream.range(0, periods.size())
            .mapToObj(
                index ->
                    new CategoryTrendResponse.TrendPoint(
                        periods.get(index),
                        counts.get(index),
                        index == 0 ? null : growthRate(counts.get(index - 1), counts.get(index))))
            .toList();
    CategoryTrendResponse response =
        new CategoryTrendResponse(
            normalizedScope,
            DONG.equals(normalizedScope) ? dongCode : null,
            category.getCatCode(),
            category.getCatName(),
            series);
    return new ApiEnvelope<>(periods.get(periods.size() - 1), periods, response);
  }

  private List<String> resolvePeriods(String periodRange) {
    String latestPeriod = analysisPeriodService.resolve(null);
    if (periodRange == null || periodRange.isBlank()) {
      return analysisPeriodService.comparisonPeriods(latestPeriod);
    }
    if (!periodRange.matches("\\d{6},\\d{6}")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "periodRange 형식은 시작YYYYMM,종료YYYYMM입니다.");
    }
    String[] bounds = periodRange.split(",", -1);
    if (bounds[0].compareTo(bounds[1]) > 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "periodRange 시작 기간이 종료 기간보다 늦습니다.");
    }
    analysisPeriodService.resolve(bounds[1]);
    List<String> periods =
        periodRepository.findAllByOrderByPeriodIdAsc().stream()
            .map(org.example.sospot.domain.Period::getPeriodId)
            .filter(period -> period.compareTo(bounds[0]) >= 0 && period.compareTo(bounds[1]) <= 0)
            .toList();
    if (periods.isEmpty() || !periods.get(0).equals(bounds[0])) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 시작 기간입니다.");
    }
    return periods;
  }

  private List<Integer> cityCounts(String catCode, List<String> periods) {
    return periods.stream()
        .map(
            period ->
                storeCountRepository.findByCatCodeAndPeriodId(catCode, period).stream()
                    .mapToInt(StoreCount::getStoreCount)
                    .sum())
        .toList();
  }

  private List<Integer> dongCounts(String dongCode, String catCode, List<String> periods) {
    Map<String, Integer> counts =
        storeCountRepository.findByDongCodeAndCatCodeOrderByPeriodIdAsc(dongCode, catCode).stream()
            .collect(Collectors.toMap(StoreCount::getPeriodId, StoreCount::getStoreCount));
    return periods.stream().map(period -> counts.getOrDefault(period, 0)).toList();
  }

  private BigDecimal growthRate(int previous, int current) {
    if (previous == 0) {
      return null;
    }
    return BigDecimal.valueOf(current - previous)
        .divide(BigDecimal.valueOf(previous), 8, RoundingMode.HALF_UP);
  }
}
