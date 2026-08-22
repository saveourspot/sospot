package org.example.sospot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record BsiResponse(
    String periodMonth, Metrics metrics, List<QuarterlyPoint> quarterlySeries) {

  public record Metrics(
      @JsonProperty("경기전반체감") BigDecimal overallSentiment,
      @JsonProperty("대전체감") BigDecimal daejeonSentiment,
      @JsonProperty("업종별체감") Map<String, BigDecimal> industrySentiment) {}

  public record QuarterlyPoint(String period, BigDecimal value) {}
}
