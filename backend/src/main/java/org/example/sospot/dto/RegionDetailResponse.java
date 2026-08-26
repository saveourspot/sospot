package org.example.sospot.dto;

import java.math.BigDecimal;
import java.util.List;

public record RegionDetailResponse(
    Header header,
    List<TopAnomaly> topAnomalies,
    List<RelativeGap> majorRelativeGaps,
    List<GrowthMomentum> growthMomentum,
    List<ExcludedCategory> excluded,
    Trend trend) {

  public record Header(
      String dongCode,
      String dongName,
      String sigungu,
      String grade,
      BigDecimal pctScore,
      int rank,
      int totalDongCount,
      short anomalyCatCount,
      short validCatCount) {}

  public record StoreCountPoint(String period, int count) {}

  public record TopAnomaly(
      String catCode,
      String catName,
      BigDecimal score,
      String grade,
      List<StoreCountPoint> storeCounts,
      BigDecimal growthRate,
      BigDecimal cityGrowthRate,
      BigDecimal relativeGap,
      BigDecimal cumChangeRate,
      boolean consecutiveDecline) {}

  public record RelativeGap(
      String categoryCode,
      String categoryName,
      BigDecimal growthRate,
      BigDecimal cityGrowthRate,
      BigDecimal relativeGap,
      String grade,
      String sampleSizeFlag) {}

  public record GrowthMomentum(
      String catCode,
      String catName,
      String momentumType,
      List<StoreCountPoint> storeCounts,
      BigDecimal growthRate,
      BigDecimal cityGrowthRate,
      BigDecimal relativeGap,
      List<String> reviewDirections,
      String caution) {}

  public record ExcludedCategory(
      String catCode, String catName, int storeCount, String reason, String sampleSizeFlag) {}

  public record Trend(String catCode, String catName, List<TrendPoint> series) {}

  public record TrendPoint(String period, int regionCount, int cityCount) {}
}
