package org.example.sospot.controller;

import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.dto.RegionDetailResponse;
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

  public RegionController(RegionDetailService regionDetailService) {
    this.regionDetailService = regionDetailService;
  }

  @GetMapping("/{dongCode}")
  public ApiEnvelope<RegionDetailResponse> getDetail(
      @PathVariable String dongCode, @RequestParam(required = false) String period) {
    return regionDetailService.getDetail(dongCode, period);
  }
}
