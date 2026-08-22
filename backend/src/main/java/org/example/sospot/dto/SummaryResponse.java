package org.example.sospot.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record SummaryResponse(
    int analyzedDongCount,
    Map<String, Long> gradeCounts,
    BigDecimal cityStoreGrowthRate,
    LatestBsi latestBsi,
    List<TopRegion> topRegions) {

  public record LatestBsi(String periodMonth, BigDecimal value) {}

  public record TopRegion(
      String dongCode, String dongName, String sigungu, String grade, BigDecimal pctScore) {}
}
