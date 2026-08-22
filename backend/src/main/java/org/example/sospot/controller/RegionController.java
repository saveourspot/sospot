package org.example.sospot.controller;

import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.dto.RegionComparisonResponse;
import org.example.sospot.dto.RegionDetailResponse;
import org.example.sospot.service.RegionComparisonService;
import org.example.sospot.service.RegionDetailService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/regions")
public class RegionController {

  private final RegionDetailService regionDetailService;
  private final RegionComparisonService regionComparisonService;

  public RegionController(
      RegionDetailService regionDetailService, RegionComparisonService regionComparisonService) {
    this.regionDetailService = regionDetailService;
    this.regionComparisonService = regionComparisonService;
  }

  @GetMapping("/compare")
  public ApiEnvelope<RegionComparisonResponse> compare(
      @RequestParam String dongA,
      @RequestParam String dongB,
      @RequestParam(required = false) String period,
      @RequestParam(required = false) String catCode) {
    return regionComparisonService.compare(dongA, dongB, period, catCode);
  }

  @GetMapping("/{dongCode}")
  public ApiEnvelope<RegionDetailResponse> getDetail(
      @PathVariable String dongCode, @RequestParam(required = false) String period) {
    return regionDetailService.getDetail(dongCode, period);
  }
}
