package org.example.sospot.dto;

import java.math.BigDecimal;
import java.util.List;

public record SelectedCategoryScoresResponse(List<Item> items) {

  public record Item(
      String dongCode,
      String dongName,
      String sigungu,
      int selectedCategoryCount,
      int validCategoryCount,
      int anomalyCategoryCount,
      String sampleSizeFlag,
      BigDecimal rawScore,
      BigDecimal score,
      String grade) {}
}
