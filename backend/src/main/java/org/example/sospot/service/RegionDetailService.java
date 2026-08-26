package org.example.sospot.service;

import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.example.sospot.domain.Anomaly;
import org.example.sospot.domain.Category;
import org.example.sospot.domain.Dong;
import org.example.sospot.domain.DongScore;
import org.example.sospot.domain.StoreCount;
import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.dto.CommercialBenchmarkResponse;
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
                        anomaly.getGrowthRate(),
                        anomaly.getCityGrowthRate(),
                        anomaly.getRelativeGap(),
                        anomaly.getGrade(),
                        anomaly.getSampleSizeFlag()))
            .toList();
    List<RegionDetailResponse.GrowthMomentum> growthMomentum =
        valid.stream()
            .map(
                anomaly ->
                    toGrowthMomentum(
                        anomaly, categories.get(anomaly.getCatCode()), comparisonPeriods))
            .filter(java.util.Objects::nonNull)
            .sorted(
                Comparator.<RegionDetailResponse.GrowthMomentum>comparingInt(
                        momentum -> momentumPriority(momentum.momentumType()))
                    .thenComparing(
                        RegionDetailResponse.GrowthMomentum::relativeGap,
                        Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(3)
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
            header, topAnomalies, relativeGaps, growthMomentum, excludedCategories, trend);
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

  public ApiEnvelope<CommercialBenchmarkResponse> getCommercialBenchmarks(
      String dongCode, String requestedPeriod) {
    Dong targetDong =
        dongRepository
            .findById(dongCode)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "지원하는 대전 행정동을 찾을 수 없습니다."));
    String period = analysisPeriodService.resolve(requestedPeriod);
    List<String> comparisonPeriods = analysisPeriodService.comparisonPeriods(period);
    Map<String, Category> categories =
        categoryRepository.findByCatLevelOrderByCatCodeAsc(MAJOR).stream()
            .collect(Collectors.toMap(Category::getCatCode, Function.identity()));
    List<StoreCount> latestCounts = storeCountRepository.findByPeriodIdAndCatLevel(period, MAJOR);
    Map<String, Map<String, Integer>> commercialMix =
        latestCounts.stream()
            .collect(
                Collectors.groupingBy(
                    StoreCount::getDongCode,
                    Collectors.toMap(StoreCount::getCatCode, StoreCount::getStoreCount)));
    Map<String, Integer> targetMix = commercialMix.get(dongCode);
    if (targetMix == null) {
      return new ApiEnvelope<>(
          period, comparisonPeriods, new CommercialBenchmarkResponse(List.of()));
    }

    Map<String, Anomaly> targetAnomalies =
        anomalyRepository
            .findByDongCodeAndPeriodIdAndCatLevelOrderByScoreDesc(dongCode, period, MAJOR)
            .stream()
            .filter(anomaly -> SAMPLE_OK.equals(anomaly.getSampleSizeFlag()))
            .collect(Collectors.toMap(Anomaly::getCatCode, Function.identity()));
    Map<String, Map<String, Anomaly>> anomaliesByDong =
        anomalyRepository.findByPeriodIdAndCatLevelAndCatCodeIn(period, MAJOR, categories.keySet())
            .stream()
            .filter(anomaly -> SAMPLE_OK.equals(anomaly.getSampleSizeFlag()))
            .collect(
                Collectors.groupingBy(
                    Anomaly::getDongCode,
                    Collectors.toMap(Anomaly::getCatCode, Function.identity())));
    Map<String, Dong> dongs =
        dongRepository.findAll().stream()
            .collect(Collectors.toMap(Dong::getDongCode, Function.identity()));

    List<CommercialBenchmarkResponse.BenchmarkRegion> similarRegions =
        commercialMix.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(dongCode))
            .map(
                entry ->
                    buildBenchmarkRegion(
                        targetDong,
                        dongs.get(entry.getKey()),
                        targetMix,
                        entry.getValue(),
                        targetAnomalies,
                        anomaliesByDong.getOrDefault(entry.getKey(), Map.of()),
                        categories,
                        comparisonPeriods))
            .filter(Objects::nonNull)
            .sorted(
                Comparator.comparing(
                    CommercialBenchmarkResponse.BenchmarkRegion::commercialMixSimilarity,
                    Comparator.reverseOrder()))
            .limit(15)
            .toList();
    List<CommercialBenchmarkResponse.BenchmarkRegion> benchmarks =
        similarRegions.stream()
            .sorted(
                Comparator.comparingInt(
                        CommercialBenchmarkResponse.BenchmarkRegion::advantageCategoryCount)
                    .reversed()
                    .thenComparing(
                        CommercialBenchmarkResponse.BenchmarkRegion::commercialMixSimilarity,
                        Comparator.reverseOrder()))
            .limit(2)
            .toList();
    return new ApiEnvelope<>(
        period, comparisonPeriods, new CommercialBenchmarkResponse(benchmarks));
  }

  private CommercialBenchmarkResponse.BenchmarkRegion buildBenchmarkRegion(
      Dong targetDong,
      Dong benchmarkDong,
      Map<String, Integer> targetMix,
      Map<String, Integer> benchmarkMix,
      Map<String, Anomaly> targetAnomalies,
      Map<String, Anomaly> benchmarkAnomalies,
      Map<String, Category> categories,
      List<String> comparisonPeriods) {
    if (benchmarkDong == null) return null;
    BigDecimal similarity = commercialMixSimilarity(targetMix, benchmarkMix, categories.keySet());
    List<CommercialBenchmarkResponse.AdvantageCategory> advantages =
        categories.values().stream()
            .map(
                category ->
                    buildAdvantageCategory(
                        targetDong,
                        benchmarkDong,
                        category,
                        targetAnomalies.get(category.getCatCode()),
                        benchmarkAnomalies.get(category.getCatCode()),
                        comparisonPeriods))
            .filter(Objects::nonNull)
            .sorted(
                Comparator.comparing(
                    CommercialBenchmarkResponse.AdvantageCategory::relativeGapDifference,
                    Comparator.reverseOrder()))
            .toList();
    if (advantages.isEmpty()) return null;
    return new CommercialBenchmarkResponse.BenchmarkRegion(
        benchmarkDong.getDongCode(),
        benchmarkDong.getDongName(),
        benchmarkDong.getSigungu(),
        similarity,
        advantages.size(),
        advantages.stream().limit(2).toList());
  }

  private CommercialBenchmarkResponse.AdvantageCategory buildAdvantageCategory(
      Dong targetDong,
      Dong benchmarkDong,
      Category category,
      Anomaly target,
      Anomaly benchmark,
      List<String> comparisonPeriods) {
    if (target == null
        || benchmark == null
        || target.getRelativeGap() == null
        || benchmark.getRelativeGap() == null
        || benchmark.getRelativeGap().compareTo(target.getRelativeGap()) <= 0) {
      return null;
    }
    BigDecimal difference = benchmark.getRelativeGap().subtract(target.getRelativeGap());
    return new CommercialBenchmarkResponse.AdvantageCategory(
        category.getCatCode(),
        category.getCatName(),
        buildStoreCountPoints(targetDong.getDongCode(), category.getCatCode(), comparisonPeriods),
        buildStoreCountPoints(benchmarkDong.getDongCode(), category.getCatCode(), comparisonPeriods),
        target.getGrowthRate(),
        benchmark.getGrowthRate(),
        target.getRelativeGap(),
        benchmark.getRelativeGap(),
        difference,
        benchmarkApplicationDirections(
            targetDong.getDongName(), benchmarkDong.getDongName(), category.getCatName()));
  }

  private BigDecimal commercialMixSimilarity(
      Map<String, Integer> left, Map<String, Integer> right, Set<String> categoryCodes) {
    double dot = 0;
    double leftNorm = 0;
    double rightNorm = 0;
    for (String catCode : categoryCodes) {
      double leftValue = left.getOrDefault(catCode, 0);
      double rightValue = right.getOrDefault(catCode, 0);
      dot += leftValue * rightValue;
      leftNorm += leftValue * leftValue;
      rightNorm += rightValue * rightValue;
    }
    double similarity =
        leftNorm == 0 || rightNorm == 0 ? 0 : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    return BigDecimal.valueOf(similarity * 100).setScale(1, java.math.RoundingMode.HALF_UP);
  }

  private List<String> benchmarkApplicationDirections(
      String targetDongName, String benchmarkDongName, String categoryName) {
    return List.of(
        benchmarkDongName + "에서 " + categoryName + " 점포의 입지·고객층·주변 업종 특징을 현장 확인",
        benchmarkDongName + "과 " + targetDongName + "의 입지·고객층·주변 업종 구성을 비교",
        targetDongName + "에 적용 가능한 요소만 소규모로 시험하고 다음 분기 변화를 재확인");
  }

  private RegionDetailResponse.GrowthMomentum toGrowthMomentum(
      Anomaly anomaly, Category category, List<String> comparisonPeriods) {
    List<RegionDetailResponse.StoreCountPoint> storeCounts =
        buildStoreCountPoints(anomaly.getDongCode(), anomaly.getCatCode(), comparisonPeriods);
    String momentumType = momentumType(anomaly, storeCounts);
    if (momentumType == null) return null;
    return new RegionDetailResponse.GrowthMomentum(
        anomaly.getCatCode(),
        category.getCatName(),
        momentumType,
        storeCounts,
        anomaly.getGrowthRate(),
        anomaly.getCityGrowthRate(),
        anomaly.getRelativeGap(),
        reviewDirections(anomaly.getCatCode()),
        "점포 수 변화만으로 성장 원인이나 정책 효과를 단정할 수 없어 현장 자료 확인이 필요합니다.");
  }

  private String momentumType(
      Anomaly anomaly, List<RegionDetailResponse.StoreCountPoint> storeCounts) {
    if (anomaly.getGrowthRate() == null || anomaly.getRelativeGap() == null) return null;
    boolean recentGrowth = anomaly.getGrowthRate().signum() > 0;
    boolean relativeAdvantage = anomaly.getRelativeGap().signum() > 0;
    if (!recentGrowth && !relativeAdvantage) return null;
    if (storeCounts.size() >= 3) {
      int first = storeCounts.get(0).count();
      int second = storeCounts.get(1).count();
      int latest = storeCounts.get(2).count();
      if (first < second && second < latest && relativeAdvantage) return "지속 성장형";
      if (first >= second && second < latest) return "회복 전환형";
    }
    return relativeAdvantage ? "상대 우위형" : "최근 증가형";
  }

  private int momentumPriority(String momentumType) {
    return switch (momentumType) {
      case "지속 성장형" -> 0;
      case "회복 전환형" -> 1;
      case "상대 우위형" -> 2;
      default -> 3;
    };
  }

  private List<String> reviewDirections(String catCode) {
    return switch (catCode) {
      case "I2" -> List.of("상권 공동 홍보·지역 브랜드 연계 가능성 확인", "보행·주차·배달 접근성의 병목 요인 점검", "임대료 상승 등 성장 부작용 모니터링");
      case "G2" -> List.of("공동 판촉·온라인 판매 지원 수요 확인", "지역 생활수요와 상품 구성의 적합성 점검", "공실·임대료 변화와 상권 유지 여건 모니터링");
      case "I1" -> List.of("지역 행사·관광 동선과의 연계 가능성 확인", "체류 수요와 교통 접근성 추가 점검", "계절성 영향을 다음 분기까지 모니터링");
      case "P1" -> List.of("지역 연령대와 교육 수요 추가 확인", "학교·공공시설 연계 프로그램 가능성 검토", "유사 교육업종 간 협업 수요 점검");
      case "Q1" -> List.of("생활권 보건의료 수요와 접근성 확인", "고령층·가족 단위 생활서비스 연계 검토", "서비스 공백 지역 여부 추가 점검");
      case "R1" -> List.of("지역 행사·생활체육 프로그램 연계 검토", "공공시설과 민간 서비스의 협업 수요 확인", "계절성과 일시적 행사 효과 모니터링");
      case "M1" -> List.of("지역 사업체의 전문서비스 수요 확인", "창업·기업지원 프로그램 연계 가능성 검토", "인접 지역 산업 기반과의 협업 여건 점검");
      case "L1" -> List.of("주거·상업 공간 변화와의 연관성 확인", "임대료와 공실 변화를 함께 모니터링", "단기 거래 증가인지 지속 흐름인지 추가 확인");
      case "N1" -> List.of("지역 사업체의 운영지원 수요 확인", "공동 물류·시설관리 협업 가능성 검토", "특정 사업체 증가에 편중됐는지 점검");
      case "S2" -> List.of("생활밀착 서비스 수요와 접근성 확인", "지역 공동 홍보·예약 채널 연계 검토", "고객층 변화와 지속 이용 가능성 점검");
      default -> List.of("현장 수요와 성장 원인 추가 확인", "인접 업종과의 연계 가능성 검토", "다음 분기까지 흐름 지속 여부 모니터링");
    };
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
