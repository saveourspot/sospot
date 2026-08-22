package org.example.sospot.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.example.sospot.domain.Bsi;
import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.dto.BsiResponse;
import org.example.sospot.repository.BsiRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BsiService {

  private static final String OVERALL_SENTIMENT = "경기전반체감";
  private static final String DAEJEON_SENTIMENT = "대전체감";
  private static final List<String> INDUSTRY_SENTIMENT_METRICS =
      List.of(
          "개인서비스업체감",
          "교육서비스업체감",
          "부동산중개업체감",
          "소매업체감",
          "수리업체감",
          "스포츠및오락관련체감",
          "음식점업체감",
          "전문기술사업체감",
          "제조업체감");

  private final AnalysisPeriodService analysisPeriodService;
  private final BsiRepository bsiRepository;

  public BsiService(AnalysisPeriodService analysisPeriodService, BsiRepository bsiRepository) {
    this.analysisPeriodService = analysisPeriodService;
    this.bsiRepository = bsiRepository;
  }

  public ApiEnvelope<BsiResponse> getBsi(String requestedPeriodMonth) {
    String analysisPeriod = analysisPeriodService.resolve(null);
    List<String> comparisonPeriods = analysisPeriodService.comparisonPeriods(analysisPeriod);
    String periodMonth = resolvePeriodMonth(requestedPeriodMonth, analysisPeriod);
    Map<String, Bsi> monthlyMetrics =
        bsiRepository.findByPeriodMonthOrderByMetricNameAsc(periodMonth).stream()
            .collect(Collectors.toMap(Bsi::getMetricName, Function.identity()));
    if (monthlyMetrics.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 BSI 기준월입니다.");
    }

    Map<String, BigDecimal> industrySentiment = new LinkedHashMap<>();
    for (String metricName : INDUSTRY_SENTIMENT_METRICS) {
      industrySentiment.put(metricName, valueOf(monthlyMetrics, metricName));
    }
    BsiResponse.Metrics metrics =
        new BsiResponse.Metrics(
            valueOf(monthlyMetrics, OVERALL_SENTIMENT),
            valueOf(monthlyMetrics, DAEJEON_SENTIMENT),
            industrySentiment);
    List<BsiResponse.QuarterlyPoint> quarterlySeries = buildQuarterlySeries(comparisonPeriods);
    return new ApiEnvelope<>(
        analysisPeriod,
        comparisonPeriods,
        new BsiResponse(periodMonth, metrics, quarterlySeries));
  }

  private String resolvePeriodMonth(String requestedPeriodMonth, String analysisPeriod) {
    String analysisMonth = analysisPeriod.substring(0, 4) + "-" + analysisPeriod.substring(4, 6);
    if (requestedPeriodMonth == null || requestedPeriodMonth.isBlank()) {
      return bsiRepository
          .findFirstByMetricNameAndValueIsNotNullAndPeriodMonthLessThanEqualOrderByPeriodMonthDesc(
              DAEJEON_SENTIMENT, analysisMonth)
          .map(Bsi::getPeriodMonth)
          .orElseThrow(
              () ->
                  new ResponseStatusException(
                      HttpStatus.SERVICE_UNAVAILABLE, "확정된 대전 체감 BSI가 없습니다."));
    }
    try {
      YearMonth.parse(requestedPeriodMonth);
    } catch (DateTimeParseException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "periodMonth 형식은 YYYY-MM입니다.", exception);
    }
    if (requestedPeriodMonth.compareTo(analysisMonth) > 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "분석 기준 분기 이후 BSI는 지원하지 않습니다.");
    }
    return requestedPeriodMonth;
  }

  private BigDecimal valueOf(Map<String, Bsi> metrics, String metricName) {
    Bsi metric = metrics.get(metricName);
    return metric == null ? null : metric.getValue();
  }

  private List<BsiResponse.QuarterlyPoint> buildQuarterlySeries(List<String> periods) {
    List<Bsi> daejeonValues =
        bsiRepository.findByMetricNameAndValueIsNotNullOrderByPeriodMonthAsc(DAEJEON_SENTIMENT);
    return periods.stream()
        .map(period -> new BsiResponse.QuarterlyPoint(period, quarterlyAverage(period, daejeonValues)))
        .toList();
  }

  private BigDecimal quarterlyAverage(String period, List<Bsi> values) {
    YearMonth end = YearMonth.of(Integer.parseInt(period.substring(0, 4)), Integer.parseInt(period.substring(4, 6)));
    YearMonth start = end.minusMonths(2);
    List<BigDecimal> quarterValues =
        values.stream()
            .filter(
                bsi -> {
                  YearMonth month = YearMonth.parse(bsi.getPeriodMonth());
                  return !month.isBefore(start) && !month.isAfter(end);
                })
            .map(Bsi::getValue)
            .toList();
    if (quarterValues.isEmpty()) {
      return null;
    }
    BigDecimal sum = quarterValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    return sum.divide(BigDecimal.valueOf(quarterValues.size()), 2, RoundingMode.HALF_UP);
  }
}
