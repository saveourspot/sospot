package org.example.sospot.service;

import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.example.sospot.domain.Anomaly;
import org.example.sospot.domain.Category;
import org.example.sospot.domain.Dong;
import org.example.sospot.domain.DongScore;
import org.example.sospot.domain.StoreCount;
import org.example.sospot.dto.ApiEnvelope;
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
public class RegionDetailService {

  private static final String MAJOR = "MAJOR";
  private static final String SAMPLE_OK = "OK";
  private static final String SAMPLE_LOW = "LOW";

  private final AnalysisPeriodService analysisPeriodService;
  private final DongRepository dongRepository;
  private final DongScoreRepository dongScoreRepository;
  private final AnomalyRepository anomalyRepository;
  private final CategoryRepository categoryRepository;
  private final StoreCountRepository storeCountRepository;

  public RegionDetailService(
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

  public ApiEnvelope<RegionDetailResponse> getDetail(String dongCode, String requestedPeriod) {
    Dong dong =
        dongRepository
            .findById(dongCode)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "지원하는 대전 행정동을 찾을 수 없습니다."));
    String period = analysisPeriodService.resolve(requestedPeriod);
    List<String> comparisonPeriods = analysisPeriodService.comparisonPeriods(period);
    List<DongScore> rankedScores =
        dongScoreRepository.findByPeriodIdOrderByPctScoreDesc(period);
    DongScore dongScore =
        dongScoreRepository
            .findByDongCodeAndPeriodId(dongCode, period)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "해당 기간의 행정동 분석 결과가 없습니다."));
    int rank = findRank(rankedScores, dongCode);

    Map<String, Category> categories =
        categoryRepository.findByCatLevelOrderByCatCodeAsc(MAJOR).stream()
            .collect(Collectors.toMap(Category::getCatCode, Function.identity()));
    List<Anomaly> allAnomalies =
        anomalyRepository.findByDongCodeAndPeriodIdAndCatLevelOrderByScoreDesc(
            dongCode, period, MAJOR);
    List<Anomaly> valid =
        allAnomalies.stream()
            .filter(anomaly -> SAMPLE_OK.equals(anomaly.getSampleSizeFlag()))
            .sorted(
                Comparator.comparing(
                    Anomaly::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
    List<Anomaly> excluded =
        allAnomalies.stream()
            .filter(anomaly -> SAMPLE_LOW.equals(anomaly.getSampleSizeFlag()))
            .toList();

    List<RegionDetailResponse.TopAnomaly> topAnomalies =
        valid.stream()
            .limit(3)
            .map(
                anomaly ->
                    toTopAnomaly(anomaly, categories.get(anomaly.getCatCode()), comparisonPeriods))
            .toList();
    List<RegionDetailResponse.RelativeGap> relativeGaps =
        allAnomalies.stream()
            .sorted(Comparator.comparing(Anomaly::getCatCode))
            .map(
                anomaly ->
                    new RegionDetailResponse.RelativeGap(
                        anomaly.getCatCode(),
                        categories.get(anomaly.getCatCode()).getCatName(),
                        anomaly.getRelativeGap(),
                        anomaly.getGrade(),
                        anomaly.getSampleSizeFlag()))
            .toList();
    List<RegionDetailResponse.ExcludedCategory> excludedCategories =
        excluded.stream()
            .map(
                anomaly ->
                    new RegionDetailResponse.ExcludedCategory(
                        anomaly.getCatCode(),
                        categories.get(anomaly.getCatCode()).getCatName(),
                        anomaly.getStoreCount(),
                        "표본 부족으로 판정 제외",
                        anomaly.getSampleSizeFlag()))
            .toList();
    RegionDetailResponse.Trend trend =
        valid.isEmpty()
            ? null
            : buildTrend(valid.get(0), categories.get(valid.get(0).getCatCode()), comparisonPeriods);

    RegionDetailResponse.Header header =
        new RegionDetailResponse.Header(
            dong.getDongCode(),
            dong.getDongName(),
            dong.getSigungu(),
            dongScore.getGrade(),
            dongScore.getPctScore(),
            rank,
            rankedScores.size(),
            dongScore.getAnomalyCatCount(),
            dongScore.getValidCatCount());
    RegionDetailResponse response =
        new RegionDetailResponse(
            header, topAnomalies, relativeGaps, excludedCategories, trend);
    return new ApiEnvelope<>(period, comparisonPeriods, response);
  }

  private int findRank(List<DongScore> rankedScores, String dongCode) {
    for (int index = 0; index < rankedScores.size(); index++) {
      if (rankedScores.get(index).getDongCode().equals(dongCode)) {
        return index + 1;
      }
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "행정동 순위를 찾을 수 없습니다.");
  }

  private RegionDetailResponse.TopAnomaly toTopAnomaly(
      Anomaly anomaly, Category category, List<String> comparisonPeriods) {
    List<RegionDetailResponse.StoreCountPoint> storeCounts =
        buildStoreCountPoints(anomaly.getDongCode(), anomaly.getCatCode(), comparisonPeriods);
    return new RegionDetailResponse.TopAnomaly(
        anomaly.getCatCode(),
        category.getCatName(),
        anomaly.getScore(),
        anomaly.getGrade(),
        storeCounts,
        anomaly.getGrowthRate(),
        anomaly.getCityGrowthRate(),
        anomaly.getRelativeGap(),
        anomaly.getCumChangeRate(),
        anomaly.getConsecutiveDecline());
  }

  private List<RegionDetailResponse.StoreCountPoint> buildStoreCountPoints(
      String dongCode, String catCode, List<String> periods) {
    Map<String, Integer> counts =
        storeCountRepository.findByDongCodeAndCatCodeOrderByPeriodIdAsc(dongCode, catCode).stream()
            .collect(Collectors.toMap(StoreCount::getPeriodId, StoreCount::getStoreCount));
    return periods.stream()
        .map(period -> new RegionDetailResponse.StoreCountPoint(period, counts.getOrDefault(period, 0)))
        .toList();
  }

  private RegionDetailResponse.Trend buildTrend(
      Anomaly anomaly, Category category, List<String> periods) {
    Map<String, Integer> regionCounts =
        buildStoreCountPoints(anomaly.getDongCode(), anomaly.getCatCode(), periods).stream()
            .collect(
                Collectors.toMap(
                    RegionDetailResponse.StoreCountPoint::period,
                    RegionDetailResponse.StoreCountPoint::count));
    List<RegionDetailResponse.TrendPoint> series =
        periods.stream()
            .map(
                period -> {
                  int cityCount =
                      storeCountRepository.findByCatCodeAndPeriodId(anomaly.getCatCode(), period).stream()
                          .mapToInt(StoreCount::getStoreCount)
                          .sum();
                  return new RegionDetailResponse.TrendPoint(
                      period, regionCounts.getOrDefault(period, 0), cityCount);
                })
            .toList();
    return new RegionDetailResponse.Trend(anomaly.getCatCode(), category.getCatName(), series);
  }
}
