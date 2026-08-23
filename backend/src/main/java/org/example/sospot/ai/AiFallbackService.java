package org.example.sospot.ai;

import org.example.sospot.ai.dto.AiChatResponse;
import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.dto.RegionDetailResponse;
import org.example.sospot.service.AnalysisPeriodService;
import org.example.sospot.service.AnomalyRegionService;
import org.example.sospot.service.RegionDetailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiFallbackService {

    private static final Logger log = LoggerFactory.getLogger(AiFallbackService.class);

    private static final String GENERIC_UNAVAILABLE =
        "AI 응답 생성에 일시적인 문제가 있습니다. 지도(F1) 또는 행정동 상세(F2) 화면에서 원하시는 지역을 직접 조회해 주세요.";

    private static final String GENERIC_NO_MATCH =
        "질문에서 대전 82개 행정동 또는 지원 업종을 식별하지 못했습니다. "
        + "예: \"판암1동은 왜 중점검토야?\", \"음식 업종 중점검토 지역 보여줘\" 와 같이 지역명이나 업종을 명시해 주세요.";

    private static final String OUTSIDE_DAEJEON =
        "SOSpot은 대전광역시 82개 행정동만 분석합니다. 다른 지역 데이터는 제공하지 않습니다.";
    private static final String UNSUPPORTED_PERIOD =
        "요청한 시점의 분석 결과가 없습니다. 현재 지원 가능한 기간은 조회 결과에서 확인해 주세요.";
    private static final String UNSUPPORTED_ANALYSIS =
        "SOSpot은 과거 3개 분기의 점포 수 변화 탐지에 집중합니다. 미래 예측, 매출, 유동인구, 개별 점포 폐업은 제공하지 않습니다.";
    private static final String CROSS_BSI =
        "BSI는 지역(17개 시도)과 업종(9개)이 각각 별도 축으로 조사되어, 지역과 업종을 교차한 값은 존재하지 않습니다. 대전 전체 체감 BSI 또는 전국 업종별 체감 BSI로만 제공됩니다.";
    private static final Set<String> OUTSIDE_REGIONS = Set.of(
        "서울", "부산", "인천", "광주", "울산", "세종", "제주",
        "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남");
    private static final Set<String> UNSUPPORTED_TERMS = Set.of(
        "미래", "예측", "매출", "유동인구", "폐업률", "폐업확률", "개별점포폐업");
    private static final Pattern PERIOD_PATTERN = Pattern.compile(
        "(20\\d{2})\\s*(?:년)?[.\\-/\\s]*(0[369]|12)\\s*(?:월)?");

    private final AliasesLoader aliasesLoader;
    private final RegionDetailService regionDetailService;
    private final AnomalyRegionService anomalyRegionService;
    private final AnalysisPeriodService analysisPeriodService;

    public AiFallbackService(
        AliasesLoader aliasesLoader,
        RegionDetailService regionDetailService,
        AnomalyRegionService anomalyRegionService,
        AnalysisPeriodService analysisPeriodService
    ) {
        this.aliasesLoader = aliasesLoader;
        this.regionDetailService = regionDetailService;
        this.anomalyRegionService = anomalyRegionService;
        this.analysisPeriodService = analysisPeriodService;
    }

    public Optional<AiChatResponse> guardrailAnswer(String question) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(question);
        Optional<String> ambiguousRegion = ambiguousRegionAnswer(normalized);
        if (ambiguousRegion.isPresent()) {
            return Optional.of(AiChatResponse.fallback(ambiguousRegion.get(), List.of()));
        }
        if (OUTSIDE_REGIONS.stream().anyMatch(normalized::contains)) {
            return Optional.of(AiChatResponse.fallback(OUTSIDE_DAEJEON, List.of()));
        }
        if (UNSUPPORTED_TERMS.stream().anyMatch(normalized::contains)) {
            return Optional.of(AiChatResponse.fallback(UNSUPPORTED_ANALYSIS, List.of()));
        }
        if ((normalized.contains("bsi") || normalized.contains("경기지수"))
            && normalized.contains("대전")
            && findCategory(question).isPresent()) {
            return Optional.of(AiChatResponse.fallback(CROSS_BSI, List.of()));
        }

        Matcher matcher = PERIOD_PATTERN.matcher(question);
        if (matcher.find()) {
            String requestedPeriod = matcher.group(1) + matcher.group(2);
            try {
                analysisPeriodService.resolve(requestedPeriod);
            } catch (RuntimeException exception) {
                return Optional.of(AiChatResponse.fallback(UNSUPPORTED_PERIOD, List.of()));
            }
        }
        return Optional.empty();
    }

    private Optional<String> ambiguousRegionAnswer(String normalizedQuestion) {
        java.util.Map<String, Set<String>> groupedRegions = new java.util.HashMap<>();
        for (AliasCatalog.RegionEntry region : aliasesLoader.catalog().regions()) {
            Matcher matcher = Pattern.compile("^(.+?)([1-9])동$").matcher(region.canonical());
            if (matcher.matches()) {
                groupedRegions.computeIfAbsent(matcher.group(1), ignored -> new TreeSet<>())
                    .add(region.canonical());
            }
        }

        for (var entry : groupedRegions.entrySet()) {
            String broadName = entry.getKey() + "동";
            boolean asksForBroadName = normalizedQuestion.contains(normalize(broadName));
            boolean namesSpecificRegion = entry.getValue().stream()
                .anyMatch(name -> normalizedQuestion.contains(normalize(name)));
            if (asksForBroadName && !namesSpecificRegion) {
                String regionNames = String.join("·", entry.getValue());
                return Optional.of(
                    broadName + "은(는) SOSpot 분석에서 단일 행정동이 아닙니다. "
                    + regionNames + "으로 나뉘므로, 조회할 행정동을 지정해 주세요. "
                    + "여러 지역을 함께 보려면 \"" + regionNames + " 전체 비교\"처럼 질문할 수 있습니다."
                );
            }
        }
        return Optional.empty();
    }

    public AiChatResponse answer(String question, Throwable cause) {
        log.warn("Fallback 착수 - cause={}", cause == null ? "n/a" : cause.getMessage());
        if (question == null || question.isBlank()) {
            return AiChatResponse.fallback(GENERIC_UNAVAILABLE, List.of());
        }

        Optional<AiChatResponse> guardrailResponse = guardrailAnswer(question);
        if (guardrailResponse.isPresent()) {
            return guardrailResponse.get();
        }

        Optional<AliasCatalog.RegionEntry> region = findRegion(question);
        Optional<AliasCatalog.CategoryEntry> category = findCategory(question);

        if (asksForPriorityCombinations(question)) {
            return priorityCombinationsAnswer();
        }
        if (region.isPresent()) {
            return regionAnswer(region.get());
        }
        if (category.isPresent()) {
            return categoryAnswer(category.get());
        }
        return AiChatResponse.fallback(GENERIC_NO_MATCH, List.of());
    }

    private boolean asksForPriorityCombinations(String question) {
        String normalized = normalize(question);
        boolean priorityIntent = normalized.contains("먼저살펴볼")
            || normalized.contains("우선검토") || normalized.contains("검토우선");
        boolean placeIntent = normalized.contains("지역") || normalized.contains("구역")
            || normalized.contains("행정동") || normalized.contains("어디");
        return priorityIntent && placeIntent;
    }

    private AiChatResponse priorityCombinationsAnswer() {
        List<AiChatResponse.ToolCall> citations = new ArrayList<>();
        try {
            var envelope = anomalyRegionService.search(null, null, "MAJOR", null, null, "score", 5);
            citations.add(new AiChatResponse.ToolCall(
                "searchAnomalyRegions",
                java.util.Map.of("catLevel", "MAJOR", "sortBy", "score", "topN", 5),
                envelope
            ));

            StringBuilder sb = new StringBuilder();
            sb.append(formatPeriod(envelope.period()))
                .append(" 기준 우선 검토 대상 상위 지역·업종 조합입니다.\n");
            var items = envelope.data().items();
            for (int i = 0; i < items.size(); i++) {
                var item = items.get(i);
                sb.append(i + 1).append(". ")
                    .append(item.sigungu()).append(" ").append(item.dongName())
                    .append(" × ").append(item.catName())
                    .append(" — ").append(item.grade())
                    .append(" (Score ").append(item.score()).append(")")
                    .append(", 직전 분기 대비 ").append(formatPercent(item.growthRate()))
                    .append(", 2분기 누적 ").append(formatPercent(item.cumChangeRate()))
                    .append(item.consecutiveDecline() ? ", 최근 2분기 연속 감소" : "")
                    .append("\n");
            }
            sb.append("등급과 Score는 절대 위험도가 아니라 대전 내 상대적 검토 우선순위입니다. ")
                .append("점포 수 감소가 개별 점포의 폐업을 의미하지 않습니다.");
            return AiChatResponse.fallback(sb.toString(), citations);
        } catch (RuntimeException exception) {
            log.warn("Fallback 우선검토 조합 조회 실패: {}", exception.getMessage());
            return AiChatResponse.fallback(GENERIC_UNAVAILABLE, citations);
        }
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
            String text = renderRegionSummary(region, detail, envelope.period());
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

    private String renderRegionSummary(
        AliasCatalog.RegionEntry region,
        RegionDetailResponse detail,
        String period
    ) {
        var header = detail.header();
        StringBuilder sb = new StringBuilder();
        sb.append(region.sigungu()).append(" ").append(header.dongName())
          .append("은(는) 기준 분기(").append(formatPeriod(period)).append(") 등급 ")
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
              .append(formatPercentPoint(top.relativeGap()))
              .append("입니다. ");
        }
        if (detail.excluded() != null && !detail.excluded().isEmpty()) {
            sb.append("표본 부족으로 판정에서 제외된 대분류 업종은 ")
              .append(detail.excluded().size())
              .append("개(")
              .append(detail.excluded().stream()
                  .map(RegionDetailResponse.ExcludedCategory::catName)
                  .collect(java.util.stream.Collectors.joining(", ")))
              .append(")입니다. 해당 조합은 기준 분기보다 두 분기 전 점포 수가 20개 미만이라 등급 산정에서 제외되었습니다. ");
        }
        sb.append("점포 수 감소가 반드시 폐업을 의미하는 것은 아닙니다.");
        sb.append(" (AI 응답 생성 실패로 결정론적 요약을 제공했습니다.)");
        return sb.toString();
    }

    private String formatPeriod(String period) {
        if (period != null && period.matches("\\d{6}")) {
            return period.substring(0, 4) + "." + period.substring(4, 6);
        }
        return period == null || period.isBlank() ? "확인되지 않음" : period;
    }

    private String formatPercentPoint(java.math.BigDecimal value) {
        if (value == null) {
            return "확인되지 않음";
        }
        return value.multiply(java.math.BigDecimal.valueOf(100))
            .setScale(1, java.math.RoundingMode.HALF_UP)
            .toPlainString() + "%p";
    }

    private String formatPercent(java.math.BigDecimal value) {
        if (value == null) {
            return "확인되지 않음";
        }
        return value.multiply(java.math.BigDecimal.valueOf(100))
            .setScale(2, java.math.RoundingMode.HALF_UP)
            .toPlainString() + "%";
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
