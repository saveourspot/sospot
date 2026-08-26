package org.example.sospot.dto;

import java.math.BigDecimal;
import java.util.List;

public record CommercialBenchmarkResponse(List<BenchmarkRegion> benchmarkRegions) {

  public record BenchmarkRegion(
      String dongCode,
      String dongName,
      String sigungu,
      BigDecimal commercialMixSimilarity,
      int advantageCategoryCount,
      List<AdvantageCategory> advantageCategories) {}

  public record AdvantageCategory(
      String catCode,
      String catName,
      List<RegionDetailResponse.StoreCountPoint> targetStoreCounts,
      List<RegionDetailResponse.StoreCountPoint> benchmarkStoreCounts,
      BigDecimal targetGrowthRate,
      BigDecimal benchmarkGrowthRate,
      BigDecimal targetRelativeGap,
      BigDecimal benchmarkRelativeGap,
      BigDecimal relativeGapDifference,
      List<String> applicationDirections) {}
}
