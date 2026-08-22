package org.example.sospot.controller;

import org.example.sospot.dto.AnomalyRegionsResponse;
import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.service.AnomalyRegionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/anomaly/regions")
public class AnomalyController {

  private final AnomalyRegionService anomalyRegionService;

  public AnomalyController(AnomalyRegionService anomalyRegionService) {
    this.anomalyRegionService = anomalyRegionService;
  }

  @GetMapping
  public ApiEnvelope<AnomalyRegionsResponse> search(
      @RequestParam(required = false) String period,
      @RequestParam(required = false) String catCode,
      @RequestParam(defaultValue = "MAJOR") String catLevel,
      @RequestParam(required = false) String grade,
      @RequestParam(required = false) Boolean consecutiveDecline,
      @RequestParam(defaultValue = "score") String sortBy,
      @RequestParam(defaultValue = "100") Integer topN) {
    return anomalyRegionService.search(
        period, catCode, catLevel, grade, consecutiveDecline, sortBy, topN);
  }
}
