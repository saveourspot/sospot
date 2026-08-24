package org.example.sospot.ai.tools;

import java.util.List;
import java.util.Map;
import org.example.sospot.service.BsiService;
import org.springframework.stereotype.Component;

@Component
public class GetBsiContextTool implements AiTool {

    private final BsiService service;

    public GetBsiContextTool(BsiService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "getBsiContext";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "name", name(),
            "description", "대전 전체 체감 BSI와 전국 업종별 BSI를 보조 경기 맥락으로 조회합니다. BSI는 이상징후 Score 계산에 사용되지 않으며 지역×업종 교차값은 제공하지 않습니다.",
            "parameters", Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                    "periodMonth", Map.of("type", "STRING", "description", "YYYY-MM 형식 기준월. 생략 시 분석 기준 분기 이내 최신 확정월.")
                ),
                "required", List.of()
            )
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        Object periodMonth = args.get("periodMonth");
        return service.getBsi(periodMonth == null ? null : periodMonth.toString());
    }
}
