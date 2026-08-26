package org.example.sospot.ai;

import org.example.sospot.ai.dto.AiChatResponse;
import org.example.sospot.dto.ApiEnvelope;
import org.example.sospot.dto.RegionDetailResponse;
import org.example.sospot.service.AnalysisPeriodService;
import org.example.sospot.service.AnomalyRegionService;
import org.example.sospot.service.RegionDetailService;
import org.example.sospot.service.RegionComparisonService;
import org.example.sospot.service.BsiService;
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
        "경기도", "강원", "충북", "충남", "전북", "전남", "경북", "경남");
    private static final Set<String> UNSUPPORTED_TERMS = Set.of(
        "미래", "예측", "앞으로", "망할", "망하는", "매출", "유동인구",
        "폐업률", "폐업확률", "개별점포폐업");
    private static final Pattern PERIOD_PATTERN = Pattern.compile(
        "(20\\d{2})\\s*(?:년)?[.\\-/\\s]*(0[369]|12)\\s*(?:월)?");

    private final AliasesLoader aliasesLoader;
    private final RegionDetailService regionDetailService;
    private final AnomalyRegionService anomalyRegionService;
    private final AnalysisPeriodService analysisPeriodService;
    private final RegionComparisonService regionComparisonService;
    private final BsiService bsiService;

    public AiFallbackService(
        AliasesLoader aliasesLoader,
        RegionDetailService regionDetailService,
        AnomalyRegionService anomalyRegionService,
        AnalysisPeriodService analysisPeriodService,
        RegionComparisonService regionComparisonService,
        BsiService bsiService
    ) {
        this.aliasesLoader = aliasesLoader;
        this.regionDetailService = regionDetailService;
        this.anomalyRegionService = anomalyRegionService;
        this.analysisPeriodService = analysisPeriodService;
        this.regionComparisonService = regionComparisonService;
        this.bsiService = bsiService;
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
        boolean asksBsi = normalized.contains("bsi") || normalized.contains("경기지수");
        boolean hasDaejeonScope = normalized.contains("대전") || !findRegions(question).isEmpty();
        if (asksBsi && hasDaejeonScope && findCategory(question).isPresent()) {
            return Optional.of(AiChatResponse.fallback(CROSS_BSI, List.of()));
        }

        Optional<String> requestedPeriod = extractRequestedPeriod(question);
        if (requestedPeriod.isPresent()) {
            try {
                analysisPeriodService.resolve(requestedPeriod.get());
            } catch (RuntimeException exception) {
                return Optional.of(AiChatResponse.fallback(UNSUPPORTED_PERIOD, List.of()));
            }
        } else {
            Matcher yearMatcher = Pattern.compile("(20\\d{2})\\s*년?").matcher(question);
            if (yearMatcher.find()) {
                try {
                    String latest = analysisPeriodService.resolve(null);
                    boolean supportedYear = analysisPeriodService.comparisonPeriods(latest).stream()
                        .anyMatch(period -> period.startsWith(yearMatcher.group(1)));
                    if (!supportedYear) {
                        return Optional.of(AiChatResponse.fallback(UNSUPPORTED_PERIOD, List.of()));
                    }
                } catch (RuntimeException exception) {
                    return Optional.of(AiChatResponse.fallback(UNSUPPORTED_PERIOD, List.of()));
                }
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

        List<AliasCatalog.RegionEntry> regions = findRegions(question);
        Optional<AliasCatalog.RegionEntry> region = regions.stream().findFirst();
        Optional<AliasCatalog.CategoryEntry> category = findCategory(question);
        String normalizedQuestion = normalize(question);

        if (normalizedQuestion.contains("bsi") || normalizedQuestion.contains("체감경기")
            || normalizedQuestion.contains("경기지수")) {
            return bsiAnswer();
        }

        boolean comparisonIntent = normalizedQuestion.contains("비교")
            || normalizedQuestion.contains("차이") || normalizedQuestion.contains("중어디")
            || normalizedQuestion.contains("더이상징후");
        if (regions.size() >= 2 && comparisonIntent) {
            return comparisonAnswer(regions.get(0), regions.get(1), category.orElse(null), question);
        }
        if (region.isEmpty() && category.isEmpty() && asksForPriorityCombinations(question)) {
            return priorityCombinationsAnswer(question);
        }
        if (region.isPresent()) {
            return regionAnswer(region.get());
        }
        if (category.isPresent()) {
            return categoryAnswer(category.get(), question);
        }
        return AiChatResponse.fallback(GENERIC_NO_MATCH, List.of());
    }

    private AiChatResponse bsiAnswer() {
        List<AiChatResponse.ToolCall> citations = new ArrayList<>();
        try {
            var envelope = bsiService.getBsi(null);
            citations.add(new AiChatResponse.ToolCall("getBsiContext", java.util.Map.of(), envelope));
            var data = envelope.data();
            String month = data.periodMonth() == null ? "확인되지 않음"
                : data.periodMonth().replace('-', '.');
            String text = month + " 기준 대전 체감 BSI는 "
                + data.metrics().daejeonSentiment() + "이며, 경기전반 체감 BSI는 "
                + data.metrics().overallSentiment() + "입니다. "
                + "BSI는 이상징후 Score나 등급 산정에 사용하지 않는 보조 경기 맥락입니다.";
            return AiChatResponse.fallback(text, citations);
        } catch (RuntimeException exception) {
            log.warn("Fallback BSI 조회 실패: {}", exception.getMessage());
            return AiChatResponse.fallback(GENERIC_UNAVAILABLE, citations);
        }
    }

    private AiChatResponse comparisonAnswer(
        AliasCatalog.RegionEntry first,
        AliasCatalog.RegionEntry second,
        AliasCatalog.CategoryEntry category,
        String question
    ) {
        List<AiChatResponse.ToolCall> citations = new ArrayList<>();
        try {
            String period = extractRequestedPeriod(question).orElse(null);
            String catCode = category == null ? null : category.catCode();
            var envelope = regionComparisonService.compare(
                first.dongCode(), second.dongCode(), period, catCode);
            java.util.Map<String, Object> args = new java.util.LinkedHashMap<>();
            args.put("dongA", first.dongCode());
            args.put("dongB", second.dongCode());
            if (period != null) args.put("period", period);
            if (catCode != null) args.put("catCode", catCode);
            citations.add(new AiChatResponse.ToolCall("compareRegions", args, envelope));

            var data = envelope.data();
            var a = data.regionA();
            var b = data.regionB();
            StringBuilder sb = new StringBuilder();
            sb.append(formatPeriod(envelope.period())).append(" 기준 ")
                .append(a.sigungu()).append(" ").append(a.dongName())
                .append("과(와) ").append(b.sigungu()).append(" ").append(b.dongName())
                .append("을 비교했습니다. ")
                .append(a.dongName()).append("은 ").append(a.grade()).append("·")
                .append(a.rank()).append("위, ")
                .append(b.dongName()).append("은 ").append(b.grade()).append("·")
                .append(b.rank()).append("위입니다. ");
            if (category != null) {
                sb.append(category.canonical()).append(" 업종 비교 결과는 도구 근거에서 확인할 수 있습니다. ");
            }
            sb.append("등급과 순위는 대전 82개 행정동 내 상대적 검토 우선순위이며, ")
                .append("점포 수 감소가 개별 점포의 폐업을 의미하지 않습니다.");
            return AiChatResponse.fallback(sb.toString(), citations);
        } catch (RuntimeException exception) {
            log.warn("Fallback 지역 비교 실패: {}", exception.getMessage());
            return AiChatResponse.fallback(GENERIC_UNAVAILABLE, citations);
        }
    }

    private boolean asksForPriorityCombinations(String question) {
        String normalized = normalize(question);
        boolean priorityIntent = normalized.contains("먼저살펴") || normalized.contains("우선검토")
            || normalized.contains("검토우선") || normalized.contains("이상징후큰")
            || normalized.contains("제일안좋") || normalized.contains("가장안좋")
            || normalized.contains("중점검토");
        boolean placeIntent = normalized.contains("지역") || normalized.contains("구역")
            || normalized.contains("행정동") || normalized.contains("어디");
        return priorityIntent && placeIntent;
    }

    private AiChatResponse priorityCombinationsAnswer(String question) {
        List<AiChatResponse.ToolCall> citations = new ArrayList<>();
        try {
            String normalized = normalize(question);
            String grade = normalized.contains("중점검토") ? "중점검토" : null;
            Integer topN = extractTopN(question).orElse(5);
            String period = extractRequestedPeriod(question).orElse(null);
            var envelope = anomalyRegionService.search(period, null, "MAJOR", grade, null, "score", topN);
            java.util.Map<String, Object> args = new java.util.LinkedHashMap<>();
            args.put("catLevel", "MAJOR");
            args.put("sortBy", "score");
            args.put("topN", topN);
            if (grade != null) args.put("grade", grade);
            if (period != null) args.put("period", period);
            citations.add(new AiChatResponse.ToolCall(
                "searchAnomalyRegions",
                args,
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
                    .append(" (점수 ").append(item.score()).append(")")
                    .append(", 직전 분기 대비 ").append(formatPercent(item.growthRate()))
                    .append(", 2분기 누적 ").append(formatPercent(item.cumChangeRate()))
                    .append(item.consecutiveDecline() ? ", 최근 2분기 연속 감소" : "")
                    .append("\n");
            }
            sb.append("등급과 점수는 절대 위험도가 아니라 대전 내 상대적 검토 우선순위입니다. ")
                .append("점포 수 감소가 개별 점포의 폐업을 의미하지 않습니다.");
            return AiChatResponse.fallback(sb.toString(), citations);
        } catch (RuntimeException exception) {
            log.warn("Fallback 우선검토 조합 조회 실패: {}", exception.getMessage());
            return AiChatResponse.fallback(GENERIC_UNAVAILABLE, citations);
        }
    }

    private Optional<Integer> extractTopN(String question) {
        Matcher matcher = Pattern.compile("(?:상위|top)\\s*(\\d{1,3})", Pattern.CASE_INSENSITIVE)
            .matcher(question);
        if (!matcher.find()) return Optional.empty();
        return Optional.of(Math.min(200, Integer.parseInt(matcher.group(1))));
    }

    private Optional<String> extractRequestedPeriod(String question) {
        Matcher quarterMatcher = Pattern.compile("(20\\d{2})\\s*년?\\s*([1-4])\\s*분기")
            .matcher(question);
        if (quarterMatcher.find()) {
            int month = Integer.parseInt(quarterMatcher.group(2)) * 3;
            return Optional.of(quarterMatcher.group(1) + String.format("%02d", month));
        }
        Matcher monthMatcher = PERIOD_PATTERN.matcher(question);
        if (monthMatcher.find()) {
            return Optional.of(monthMatcher.group(1) + monthMatcher.group(2));
        }
        return Optional.empty();
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

    private AiChatResponse categoryAnswer(AliasCatalog.CategoryEntry category, String question) {
        String catLevel = "MAJOR".equalsIgnoreCase(category.catLevel()) ? "MAJOR" : "MIDDLE";
        List<AiChatResponse.ToolCall> citations = new ArrayList<>();
        try {
            String normalized = normalize(question);
            String grade = java.util.List.of("중점검토", "주의", "관심", "정상").stream()
                .filter(normalized::contains).findFirst().orElse(null);
            Boolean consecutiveDecline = normalized.contains("연속감소")
                || normalized.contains("연속으로감소") ? Boolean.TRUE : null;
            String period = extractRequestedPeriod(question).orElse(null);
            Integer topN = extractTopN(question).orElse(5);
            var envelope = anomalyRegionService.search(
                period, category.catCode(), catLevel, grade, consecutiveDecline, "score", topN);
            java.util.Map<String, Object> args = new java.util.LinkedHashMap<>();
            args.put("catCode", category.catCode());
            args.put("catLevel", catLevel);
            args.put("topN", topN);
            if (grade != null) args.put("grade", grade);
            if (consecutiveDecline != null) args.put("consecutiveDecline", consecutiveDecline);
            if (period != null) args.put("period", period);
            citations.add(new AiChatResponse.ToolCall(
                "searchAnomalyRegions",
                args,
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
        sb.append("## ").append(header.dongName()).append(" 분석 요약\n\n")
          .append("- **기준 분기:** ").append(formatPeriod(period)).append("\n")
          .append("- **행정동 등급:** ").append(header.grade())
          .append(" — 대전 82개 행정동 중 ").append(header.rank()).append("위\n");
        if (detail.topAnomalies() != null && !detail.topAnomalies().isEmpty()) {
            var top = detail.topAnomalies().get(0);
            sb.append("- **가장 두드러진 이상 업종:** ").append(top.catName())
              .append(" — 점수 ").append(top.score())
              .append(", 상대격차 ").append(formatPercentPoint(top.relativeGap()))
              .append("\n");
        }
        if (detail.growthMomentum() != null && !detail.growthMomentum().isEmpty()) {
            sb.append("\n## 성장 모멘텀과 정책 검토 방향\n");
            for (var momentum : detail.growthMomentum()) {
                sb.append("\n### ").append(momentum.catName())
                  .append(" · ").append(momentum.momentumType()).append("\n\n")
                  .append("- **최근 지역 증감률:** ").append(formatPercent(momentum.growthRate())).append("\n")
                  .append("- **대전 대비 상대격차:** ").append(formatPercentPoint(momentum.relativeGap())).append("\n");
                if (momentum.reviewDirections() != null) {
                    for (String direction : momentum.reviewDirections()) {
                        sb.append("- **검토:** ").append(direction).append("\n");
                    }
                }
            }
        } else {
            sb.append("\n## 성장 모멘텀\n\n")
              .append("현재 기준에서 표시할 성장 모멘텀 업종이 없습니다.\n");
        }
        if (detail.excluded() != null && !detail.excluded().isEmpty()) {
            sb.append("\n## 판정 제외\n\n")
              .append("- **표본 부족 업종 ").append(detail.excluded().size()).append("개:** ")
              .append(detail.excluded().stream()
                  .map(RegionDetailResponse.ExcludedCategory::catName)
                  .collect(java.util.stream.Collectors.joining(", ")))
              .append("\n");
        }
        sb.append("\n> 정책 검토 방향은 자동 결정이나 효과 예측이 아닙니다. 점포 수 변화만으로 성장 원인을 단정할 수 없어 현장 자료 확인이 필요하며, 점포 수 감소가 개별 점포의 폐업을 의미하지 않습니다.");
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
            sb.append("상위 ").append(response.items().size()).append("개: ");
            for (int i = 0; i < response.items().size(); i++) {
                var item = response.items().get(i);
                if (i > 0) sb.append(", ");
                sb.append(item.dongName()).append("(점수 ").append(item.score()).append(", ").append(item.grade()).append(")");
            }
            sb.append(". ");
        } else {
            sb.append("조건에 맞는 결과가 없습니다. ");
        }
        return sb.toString();
    }

    private Optional<AliasCatalog.RegionEntry> findRegion(String question) {
        return findRegions(question).stream().findFirst();
    }

    private List<AliasCatalog.RegionEntry> findRegions(String question) {
        String haystack = normalize(question);
        List<AliasCatalog.RegionEntry> matches = new ArrayList<>();
        for (AliasCatalog.RegionEntry region : aliasesLoader.catalog().regions()) {
            for (String alias : region.aliases()) {
                if (alias.length() >= 2 && haystack.contains(normalize(alias))) {
                    matches.add(region);
                    break;
                }
            }
        }
        return matches;
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
