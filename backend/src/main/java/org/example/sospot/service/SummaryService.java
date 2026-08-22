package org.example.sospot.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.example.sospot.domain.Dong;
import org.example.sospot.domain.DongScore;
import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.dto.SummaryResponse;
import org.example.sospot.repository.BsiRepository;
import org.example.sospot.repository.DongRepository;
import org.example.sospot.repository.DongScoreRepository;
import org.example.sospot.repository.StoreCountRepository;
import org.springframework.stereotype.Service;

@Service
public class SummaryService {

  private static final List<String> GRADE_ORDER = List.of("중점검토", "주의", "관심", "정상");
  private static final String CITY_BSI_METRIC = "대전체감";

  private final AnalysisPeriodService analysisPeriodService;
  private final DongScoreRepository dongScoreRepository;
  private final DongRepository dongRepository;
  private final StoreCountRepository storeCountRepository;
  private final BsiRepository bsiRepository;

  public SummaryService(
      AnalysisPeriodService analysisPeriodService,
      DongScoreRepository dongScoreRepository,
      DongRepository dongRepository,
      StoreCountRepository storeCountRepository,
      BsiRepository bsiRepository) {
    this.analysisPeriodService = analysisPeriodService;
    this.dongScoreRepository = dongScoreRepository;
    this.dongRepository = dongRepository;
    this.storeCountRepository = storeCountRepository;
    this.bsiRepository = bsiRepository;
  }

  public ApiEnvelope<SummaryResponse> getSummary(String requestedPeriod) {
    String period = analysisPeriodService.resolve(requestedPeriod);
    List<String> comparisonPeriods = analysisPeriodService.comparisonPeriods(period);
    List<DongScore> scores = dongScoreRepository.findByPeriodIdOrderByPctScoreDesc(period);
    Map<String, Dong> dongByCode =
        dongRepository.findAll().stream()
            .collect(Collectors.toMap(Dong::getDongCode, Function.identity()));

    Map<String, Long> gradeCounts = new LinkedHashMap<>();
    for (String grade : GRADE_ORDER) {
      gradeCounts.put(grade, scores.stream().filter(score -> grade.equals(score.getGrade())).count());
    }

    List<SummaryResponse.TopRegion> topRegions =
        scores.stream()
            .limit(5)
            .map(
                score -> {
                  Dong dong = dongByCode.get(score.getDongCode());
                  return new SummaryResponse.TopRegion(
                      score.getDongCode(),
                      dong.getDongName(),
                      dong.getSigungu(),
                      score.getGrade(),
                      score.getPctScore());
                })
            .toList();

    BigDecimal cityStoreGrowthRate = cityStoreGrowthRate(period, comparisonPeriods);
    SummaryResponse.LatestBsi latestBsi = latestBsi(period);
    SummaryResponse response =
        new SummaryResponse(scores.size(), gradeCounts, cityStoreGrowthRate, latestBsi, topRegions);
    return new ApiEnvelope<>(period, comparisonPeriods, response);
  }

  private BigDecimal cityStoreGrowthRate(String period, List<String> comparisonPeriods) {
    int currentIndex = comparisonPeriods.indexOf(period);
    if (currentIndex <= 0) {
      return null;
    }
    String previousPeriod = comparisonPeriods.get(currentIndex - 1);
    Long current = storeCountRepository.sumStoreCountByPeriodIdAndCatLevel(period, "MAJOR");
    Long previous =
        storeCountRepository.sumStoreCountByPeriodIdAndCatLevel(previousPeriod, "MAJOR");
    if (current == null || previous == null || previous == 0) {
      return null;
    }
    return BigDecimal.valueOf(current - previous)
        .divide(BigDecimal.valueOf(previous), 8, RoundingMode.HALF_UP);
  }

  private SummaryResponse.LatestBsi latestBsi(String period) {
    String periodMonth = period.substring(0, 4) + "-" + period.substring(4, 6);
    return bsiRepository
        .findFirstByMetricNameAndValueIsNotNullAndPeriodMonthLessThanEqualOrderByPeriodMonthDesc(
            CITY_BSI_METRIC, periodMonth)
        .map(bsi -> new SummaryResponse.LatestBsi(bsi.getPeriodMonth(), bsi.getValue()))
        .orElse(null);
  }
}
