package org.example.sospot.dto;

import java.math.BigDecimal;
import java.util.List;

public record RegionScoresResponse(List<Item> items) {

  public record Item(
      String dongCode,
      String dongName,
      String sigungu,
      BigDecimal pctScore,
      BigDecimal rawScore,
      String grade,
      short anomalyCatCount,
      short validCatCount) {}
}
