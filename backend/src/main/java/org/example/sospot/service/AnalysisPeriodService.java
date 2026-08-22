package org.example.sospot.service;

import java.util.List;
import org.example.sospot.repository.DongScoreRepository;
import org.example.sospot.repository.PeriodRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnalysisPeriodService {

  private final DongScoreRepository dongScoreRepository;
  private final PeriodRepository periodRepository;

  public AnalysisPeriodService(
      DongScoreRepository dongScoreRepository, PeriodRepository periodRepository) {
    this.dongScoreRepository = dongScoreRepository;
    this.periodRepository = periodRepository;
  }

  public String resolve(String requestedPeriod) {
    if (requestedPeriod == null || requestedPeriod.isBlank()) {
      return dongScoreRepository
          .findLatestAnalyzedPeriodId()
          .orElseThrow(
              () ->
                  new ResponseStatusException(
                      HttpStatus.SERVICE_UNAVAILABLE, "완료된 분석 기간이 없습니다."));
    }

    if (!requestedPeriod.matches("\\d{6}")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "period 형식은 YYYYMM입니다.");
    }
    if (!dongScoreRepository.existsByPeriodId(requestedPeriod)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "지원하지 않는 분석 기간입니다: " + requestedPeriod);
    }
    return requestedPeriod;
  }

  public List<String> comparisonPeriods(String periodId) {
    List<String> periods =
        periodRepository.findAllByOrderByPeriodIdAsc().stream()
            .map(org.example.sospot.domain.Period::getPeriodId)
            .filter(candidate -> candidate.compareTo(periodId) <= 0)
            .toList();
    return periods.subList(Math.max(0, periods.size() - 3), periods.size());
  }
}
