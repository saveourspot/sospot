package org.example.sospot.service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.example.sospot.domain.Dong;
import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.dto.RegionScoresResponse;
import org.example.sospot.repository.DongRepository;
import org.example.sospot.repository.DongScoreRepository;
import org.springframework.stereotype.Service;

@Service
public class RegionScoreService {

  private final AnalysisPeriodService analysisPeriodService;
  private final DongScoreRepository dongScoreRepository;
  private final DongRepository dongRepository;

  public RegionScoreService(
      AnalysisPeriodService analysisPeriodService,
      DongScoreRepository dongScoreRepository,
      DongRepository dongRepository) {
    this.analysisPeriodService = analysisPeriodService;
    this.dongScoreRepository = dongScoreRepository;
    this.dongRepository = dongRepository;
  }

  public ApiEnvelope<RegionScoresResponse> getScores(String requestedPeriod) {
    String period = analysisPeriodService.resolve(requestedPeriod);
    Map<String, Dong> dongByCode =
        dongRepository.findAll().stream()
            .collect(Collectors.toMap(Dong::getDongCode, Function.identity()));
    var items =
        dongScoreRepository.findByPeriodIdOrderByPctScoreDesc(period).stream()
            .map(
                score -> {
                  Dong dong = dongByCode.get(score.getDongCode());
                  return new RegionScoresResponse.Item(
                      score.getDongCode(),
                      dong.getDongName(),
                      dong.getSigungu(),
                      score.getPctScore(),
                      score.getRawScore(),
                      score.getGrade(),
                      score.getAnomalyCatCount(),
                      score.getValidCatCount());
                })
            .toList();
    return new ApiEnvelope<>(
        period,
        analysisPeriodService.comparisonPeriods(period),
        new RegionScoresResponse(items));
  }
}
