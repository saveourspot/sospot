package org.example.sospot.controller;

import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.dto.SummaryResponse;
import org.example.sospot.service.SummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/summary")
public class SummaryController {

  private final SummaryService summaryService;

  public SummaryController(SummaryService summaryService) {
    this.summaryService = summaryService;
  }

  @GetMapping
  public ApiEnvelope<SummaryResponse> getSummary(
      @RequestParam(required = false) String period) {
    return summaryService.getSummary(period);
  }
}
