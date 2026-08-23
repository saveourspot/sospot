package org.example.sospot.ai.tools;

import org.example.sospot.service.AnomalyRegionService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SearchAnomalyRegionsTool implements AiTool {

    private final AnomalyRegionService service;

    public SearchAnomalyRegionsTool(AnomalyRegionService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "searchAnomalyRegions";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "name", name(),
            "description",
                "대전 행정동 × 업종 조합의 이상징후 목록을 검색합니다. "
                + "특정 업종·등급·연속감소 조건에 맞는 상위 조합을 반환합니다. "
                + "period를 지정하지 않으면 최신 분석 완료 분기를 사용합니다.",
            "parameters", Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                    "period", Map.of("type", "STRING", "description", "YYYYMM 형식 분기. 생략 시 최신."),
                    "catCode", Map.of("type", "STRING", "description", "업종 코드. 대분류 예: I2 음식, G2 소매. 중분류 예: I212 비알코올. 생략 시 전체 업종."),
                    "catLevel", Map.of("type", "STRING", "enum", List.of("MAJOR", "MIDDLE"), "description", "업종 레벨. 지도 기본은 MAJOR."),
                    "grade", Map.of("type", "STRING", "enum", List.of("중점검토", "주의", "관심", "정상"), "description", "등급 필터. 생략 시 전체."),
                    "consecutiveDecline", Map.of("type", "BOOLEAN", "description", "true 시 최근 2분기 연속 감소 조합만."),
                    "sortBy", Map.of("type", "STRING", "enum", List.of("score", "relativeGap", "cumChange"), "description", "정렬 기준. 기본 score."),
                    "topN", Map.of("type", "INTEGER", "description", "상위 N개. 기본 100, 최대 200.")
                ),
                "required", List.of("catLevel")
            )
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        return service.search(
            asString(args, "period"),
            asString(args, "catCode"),
            asString(args, "catLevel"),
            asString(args, "grade"),
            (Boolean) args.get("consecutiveDecline"),
            asString(args, "sortBy"),
            asInteger(args, "topN")
        );
    }

    private static String asString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : value.toString();
    }

    private static Integer asInteger(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(value.toString());
    }
}
