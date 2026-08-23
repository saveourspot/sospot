package org.example.sospot.ai.tools;

import org.example.sospot.service.RegionComparisonService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CompareRegionsTool implements AiTool {

    private final RegionComparisonService service;

    public CompareRegionsTool(RegionComparisonService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "compareRegions";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "name", name(),
            "description",
                "두 행정동의 이상징후·업종 분포를 비교합니다. "
                + "catCode를 지정하면 특정 업종에 초점을 맞추고, 생략 시 전체 대분류 업종을 비교합니다.",
            "parameters", Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                    "dongA", Map.of("type", "STRING", "description", "첫 번째 행정동 코드 8자리."),
                    "dongB", Map.of("type", "STRING", "description", "두 번째 행정동 코드 8자리."),
                    "period", Map.of("type", "STRING", "description", "YYYYMM. 생략 시 최신."),
                    "catCode", Map.of("type", "STRING", "description", "특정 업종 코드. 생략 시 전체 대분류.")
                ),
                "required", List.of("dongA", "dongB")
            )
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        return service.compare(
            (String) args.get("dongA"),
            (String) args.get("dongB"),
            asString(args, "period"),
            asString(args, "catCode")
        );
    }

    private static String asString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : value.toString();
    }
}
