package org.example.sospot.ai.tools;

import org.example.sospot.service.RegionDetailService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GetRegionDetailTool implements AiTool {

    private final RegionDetailService service;

    public GetRegionDetailTool(RegionDetailService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "getRegionDetail";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "name", name(),
            "description",
                "특정 행정동의 상세 분석 결과를 반환합니다. "
                + "이상 업종 TOP 카드, 지역/대전 추세, 판정 제외 업종을 포함합니다.",
            "parameters", Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                    "dongCode", Map.of("type", "STRING", "description", "행정동 코드 8자리. 예: 30110551 (판암1동)."),
                    "period", Map.of("type", "STRING", "description", "YYYYMM 분기. 생략 시 최신 분석 완료 분기.")
                ),
                "required", List.of("dongCode")
            )
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        return service.getDetail(
            (String) args.get("dongCode"),
            asString(args, "period")
        );
    }

    private static String asString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : value.toString();
    }
}
