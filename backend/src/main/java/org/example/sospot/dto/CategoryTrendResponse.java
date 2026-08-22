package org.example.sospot.dto;

import java.math.BigDecimal;
import java.util.List;

public record CategoryTrendResponse(
    String scope,
    String dongCode,
    String catCode,
    String catName,
    List<TrendPoint> series) {

  public record TrendPoint(String period, int storeCount, BigDecimal growthRate) {}
}
