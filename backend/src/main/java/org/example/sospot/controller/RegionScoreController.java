package org.example.sospot.controller;

import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.dto.RegionScoresResponse;
import org.example.sospot.service.RegionScoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/regions/scores")
public class RegionScoreController {

  private final RegionScoreService regionScoreService;

  public RegionScoreController(RegionScoreService regionScoreService) {
    this.regionScoreService = regionScoreService;
  }

  @GetMapping
  public ApiEnvelope<RegionScoresResponse> getScores(
      @RequestParam(required = false) String period) {
    return regionScoreService.getScores(period);
  }
}
