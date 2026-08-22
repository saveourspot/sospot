package org.example.sospot.controller;

import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.dto.CategoryTrendResponse;
import org.example.sospot.service.CategoryTrendService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

  private final CategoryTrendService categoryTrendService;

  public CategoryController(CategoryTrendService categoryTrendService) {
    this.categoryTrendService = categoryTrendService;
  }

  @GetMapping("/{catCode}/trend")
  public ApiEnvelope<CategoryTrendResponse> getTrend(
      @PathVariable String catCode,
      @RequestParam(defaultValue = "city") String scope,
      @RequestParam(required = false) String dongCode,
      @RequestParam(required = false) String periodRange) {
    return categoryTrendService.getTrend(catCode, scope, dongCode, periodRange);
  }
}
