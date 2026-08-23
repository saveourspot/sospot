package org.example.sospot.ai;

import org.example.sospot.ai.dto.AiChatResponse;
import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.dto.RegionDetailResponse;
import org.example.sospot.service.AnomalyRegionService;
import org.example.sospot.service.RegionDetailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AiFallbackService {

    private static final Logger log = LoggerFactory.getLogger(AiFallbackService.class);

    private static final String GENERIC_UNAVAILABLE =
        "AI 응답 생성에 일시적인 문제가 있습니다. 지도(F1) 또는 행정동 상세(F2) 화면에서 원하시는 지역을 직접 조회해 주세요.";

    private static final String GENERIC_NO_MATCH =
        "질문에서 대전 82개 행정동 또는 지원 업종을 식별하지 못했습니다. "
        + "예: \"판암1동은 왜 중점검토야?\", \"음식 업종 중점검토 지역 보여줘\" 와 같이 지역명이나 업종을 명시해 주세요.";

    private final AliasesLoader aliasesLoader;
    private final RegionDetailService regionDetailService;
    private final AnomalyRegionService anomalyRegionService;

    public AiFallbackService(
        AliasesLoader aliasesLoader,
        RegionDetailService regionDetailService,
        AnomalyRegionService anomalyRegionService
    ) {
        this.aliasesLoader = aliasesLoader;
        this.regionDetailService = regionDetailService;
        this.anomalyRegionService = anomalyRegionService;
    }

    public AiChatResponse answer(String question, Throwable cause) {
        log.warn("Fallback 착수 - cause={}", cause == null ? "n/a" : cause.getMessage());
        if (question == null || question.isBlank()) {
            return AiChatResponse.fallback(GENERIC_UNAVAILABLE, List.of());
        }

        Optional<AliasCatalog.RegionEntry> region = findRegion(question);
        Optional<AliasCatalog.CategoryEntry> category = findCategory(question);

        if (region.isPresent()) {
            return regionAnswer(region.get());
        }
        if (category.isPresent()) {
            return categoryAnswer(category.get());
        }
        return AiChatResponse.fallback(GENERIC_NO_MATCH, List.of());
    }

    private AiChatResponse regionAnswer(AliasCatalog.RegionEntry region) {
        List<AiChatResponse.ToolCall> citations = new ArrayList<>();
        try {
            ApiEnvelope<RegionDetailResponse> envelope = regionDetailService.getDetail(region.dongCode(), null);
            RegionDetailResponse detail = envelope.data();
            citations.add(new AiChatResponse.ToolCall(
                "getRegionDetail",
                java.util.Map.of("dongCode", region.dongCode()),
                envelope
            ));
            String text = renderRegionSummary(region, detail);
            return AiChatResponse.fallback(text, citations);
        } catch (RuntimeException e) {
            log.warn("Fallback 지역 상세 조회 실패: {}", e.getMessage());
            return AiChatResponse.fallback(GENERIC_UNAVAILABLE, citations);
        }
    }

    private AiChatResponse categoryAnswer(AliasCatalog.CategoryEntry category) {
        String catLevel = "MAJOR".equalsIgnoreCase(category.catLevel()) ? "MAJOR" : "MIDDLE";
        List<AiChatResponse.ToolCall> citations = new ArrayList<>();
        try {
            var envelope = anomalyRegionService.search(null, category.catCode(), catLevel, null, null, "score", 5);
            citations.add(new AiChatResponse.ToolCall(
                "searchAnomalyRegions",
                java.util.Map.of("catCode", category.catCode(), "catLevel", catLevel, "topN", 5),
                envelope
            ));
            String text = renderCategorySummary(category, envelope.data());
            return AiChatResponse.fallback(text, citations);
        } catch (RuntimeException e) {
            log.warn("Fallback 업종 검색 실패: {}", e.getMessage());
            return AiChatResponse.fallback(GENERIC_UNAVAILABLE, citations);
        }
    }

    private String renderRegionSummary(AliasCatalog.RegionEntry region, RegionDetailResponse detail) {
        var header = detail.header();
        StringBuilder sb = new StringBuilder();
        sb.append(region.sigungu()).append(" ").append(header.dongName())
          .append("은(는) 기준 분기(2026.06) 등급 ")
          .append(header.grade())
          .append(", 대전 82개 행정동 중 ")
          .append(header.rank())
          .append("위입니다. ");
        if (detail.topAnomalies() != null && !detail.topAnomalies().isEmpty()) {
            var top = detail.topAnomalies().get(0);
            sb.append("가장 두드러진 이상 업종은 ")
              .append(top.catName())
              .append("이며, Score ")
              .append(top.score())
              .append(", 상대격차 ")
              .append(top.relativeGap())
              .append("입니다. ");
        }
        sb.append("점포 수 감소가 반드시 폐업을 의미하는 것은 아닙니다.");
        sb.append(" (AI 응답 생성 실패로 결정론적 요약을 제공했습니다.)");
        return sb.toString();
    }

    private String renderCategorySummary(AliasCatalog.CategoryEntry category, org.example.sospot.dto.AnomalyRegionsResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append(category.canonical()).append(" 업종(코드 ").append(category.catCode()).append(")의 이상징후 상위 지역입니다. ");
        if (response.items() != null && !response.items().isEmpty()) {
            sb.append("Top ").append(response.items().size()).append(": ");
            for (int i = 0; i < response.items().size(); i++) {
                var item = response.items().get(i);
                if (i > 0) sb.append(", ");
                sb.append(item.dongName()).append("(Score ").append(item.score()).append(", ").append(item.grade()).append(")");
            }
            sb.append(". ");
        } else {
            sb.append("조건에 맞는 결과가 없습니다. ");
        }
        sb.append("(AI 응답 생성 실패로 결정론적 요약을 제공했습니다.)");
        return sb.toString();
    }

    private Optional<AliasCatalog.RegionEntry> findRegion(String question) {
        String haystack = normalize(question);
        for (AliasCatalog.RegionEntry region : aliasesLoader.catalog().regions()) {
            for (String alias : region.aliases()) {
                if (alias.length() >= 2 && haystack.contains(normalize(alias))) {
                    return Optional.of(region);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<AliasCatalog.CategoryEntry> findCategory(String question) {
        String haystack = normalize(question);
        AliasCatalog.CategoryEntry best = null;
        int bestLen = 0;
        for (AliasCatalog.CategoryEntry category : aliasesLoader.catalog().categories()) {
            for (String alias : category.aliases()) {
                if (alias.length() >= 2 && haystack.contains(normalize(alias)) && alias.length() > bestLen) {
                    best = category;
                    bestLen = alias.length();
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
