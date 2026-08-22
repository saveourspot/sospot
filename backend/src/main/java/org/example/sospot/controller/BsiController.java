package org.example.sospot.controller;

import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.dto.BsiResponse;
import org.example.sospot.service.BsiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bsi")
public class BsiController {

  private final BsiService bsiService;

  public BsiController(BsiService bsiService) {
    this.bsiService = bsiService;
  }

  @GetMapping
  public ApiEnvelope<BsiResponse> getBsi(
      @RequestParam(required = false) String periodMonth) {
    return bsiService.getBsi(periodMonth);
  }
}
