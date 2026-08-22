package org.example.sospot.dto;

import java.math.BigDecimal;
import java.util.List;

public record AnomalyRegionsResponse(List<Item> items) {

  public record Item(
      String dongCode,
      String dongName,
      String sigungu,
      String catCode,
      String catName,
      String catLevel,
      int storeCount,
      BigDecimal growthRate,
      BigDecimal cityGrowthRate,
      BigDecimal relativeGap,
      BigDecimal cumChangeRate,
      boolean consecutiveDecline,
      String sampleSizeFlag,
      BigDecimal score,
      String grade) {}
}
