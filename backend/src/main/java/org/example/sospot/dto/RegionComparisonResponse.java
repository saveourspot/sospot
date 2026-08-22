package org.example.sospot.dto;

import java.math.BigDecimal;
import java.util.List;

public record RegionComparisonResponse(
    Region regionA, Region regionB, Comparison comparison) {

  public record Region(
      String dongCode,
      String dongName,
      String sigungu,
      String grade,
      BigDecimal pctScore,
      int rank,
      short anomalyCatCount,
      short validCatCount,
      List<CategoryMetric> categories) {}

  public record CategoryMetric(
      String catCode,
      String catName,
      String sampleSizeFlag,
      BigDecimal score,
      String grade,
      List<RegionDetailResponse.StoreCountPoint> storeCounts,
      BigDecimal growthRate,
      BigDecimal cityGrowthRate,
      BigDecimal relativeGap,
      BigDecimal cumChangeRate,
      Boolean consecutiveDecline) {}

  public record Comparison(
      String higherPriorityDongCode,
      BigDecimal pctScoreGap,
      List<CategoryDifference> categories) {}

  public record CategoryDifference(
      String catCode,
      String catName,
      BigDecimal scoreGap,
      BigDecimal relativeGapGap,
      String moreNegativeRelativeGapDongCode) {}
}
