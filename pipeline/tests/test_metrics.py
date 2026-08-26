from __future__ import annotations

import math
import unittest

import numpy as np
import pandas as pd

from pipeline.src.metrics import (
    _grade_for_dong_pct,
    _grade_for_score,
    compute_combination_metrics,
    compute_dong_scores,
    compute_scores,
    determine_analyzable_periods,
)


def _make_counts(rows: list[tuple[str, str, str, int]]) -> pd.DataFrame:
    return pd.DataFrame(
        [
            {
                "dong_code": dong,
                "cat_code": cat,
                "period_id": period,
                "cat_level": "MAJOR",
                "store_count": count,
            }
            for dong, cat, period, count in rows
        ]
    )


class DetermineAnalyzablePeriodsTest(unittest.TestCase):
    def test_returns_only_third_of_three_consecutive_quarters(self) -> None:
        counts = _make_counts(
            [
                ("30140550", "I2", "202512", 123),
                ("30140550", "I2", "202603", 121),
                ("30140550", "I2", "202606", 117),
            ]
        )

        self.assertEqual(determine_analyzable_periods(counts), ["202606"])

    def test_skips_when_a_quarter_is_missing_in_the_window(self) -> None:
        counts = _make_counts(
            [
                ("30140550", "I2", "202512", 123),
                ("30140550", "I2", "202606", 117),
            ]
        )

        self.assertEqual(determine_analyzable_periods(counts), [])

    def test_returns_multiple_targets_when_more_quarters_are_stored(self) -> None:
        counts = _make_counts(
            [
                ("30140550", "I2", "202509", 130),
                ("30140550", "I2", "202512", 123),
                ("30140550", "I2", "202603", 121),
                ("30140550", "I2", "202606", 117),
            ]
        )

        self.assertEqual(determine_analyzable_periods(counts), ["202603", "202606"])


class ComputeCombinationMetricsTest(unittest.TestCase):
    def test_growth_relative_gap_and_cum_match_reference_case(self) -> None:
        counts = _make_counts(
            [
                # 목동 음식업 사례: 123 → 121 → 117
                ("30140550", "I2", "202512", 123),
                ("30140550", "I2", "202603", 121),
                ("30140550", "I2", "202606", 117),
                # 대전 전체 음식업(가상): 202603=200, 202606=206 → G=+3%
                ("30140560", "I2", "202512", 77),
                ("30140560", "I2", "202603", 79),
                ("30140560", "I2", "202606", 89),
            ]
        )

        metrics = compute_combination_metrics(counts, "202606")
        target_row = metrics[metrics["dong_code"] == "30140550"].iloc[0]

        self.assertAlmostEqual(target_row["growth_rate"], (117 - 121) / 121, places=6)
        self.assertAlmostEqual(target_row["city_growth_rate"], (206 - 200) / 200, places=6)
        self.assertAlmostEqual(
            target_row["relative_gap"],
            target_row["growth_rate"] - target_row["city_growth_rate"],
            places=6,
        )
        self.assertAlmostEqual(target_row["cum_change_rate"], (117 - 123) / 123, places=6)
        self.assertEqual(target_row["sample_size_flag"], "OK")

    def test_low_sample_flag_when_n_t_minus_two_below_threshold(self) -> None:
        counts = _make_counts(
            [
                ("30110515", "R1", "202512", 15),
                ("30110515", "R1", "202603", 16),
                ("30110515", "R1", "202606", 14),
            ]
        )

        metrics = compute_combination_metrics(counts, "202606")
        row = metrics.iloc[0]

        self.assertEqual(row["sample_size_flag"], "LOW")

    def test_consecutive_decline_flag_true_only_when_g_t_and_g_prev_negative(self) -> None:
        counts = _make_counts(
            [
                # 감소 → 감소: True
                ("30110515", "A1", "202512", 100),
                ("30110515", "A1", "202603", 95),
                ("30110515", "A1", "202606", 90),
                # 증가 → 감소: False
                ("30110515", "A2", "202512", 50),
                ("30110515", "A2", "202603", 55),
                ("30110515", "A2", "202606", 52),
            ]
        )

        metrics = compute_combination_metrics(counts, "202606")
        by_cat = metrics.set_index("cat_code")

        self.assertTrue(bool(by_cat.loc["A1", "consecutive_decline"]))
        self.assertFalse(bool(by_cat.loc["A2", "consecutive_decline"]))

    def test_missing_previous_period_treated_as_zero_and_produces_nan_ratios(self) -> None:
        counts = _make_counts(
            [
                # 다른 조합이 세 분기 모두 존재 → 분기 자체는 유효
                ("30110515", "I2", "202512", 100),
                ("30110515", "I2", "202603", 100),
                ("30110515", "I2", "202606", 100),
                # 관심 조합(Z9)은 t 시점에만 신규 등장
                ("30110515", "Z9", "202606", 5),
            ]
        )

        metrics = compute_combination_metrics(counts, "202606")
        row = metrics[metrics["cat_code"] == "Z9"].iloc[0]

        self.assertTrue(math.isnan(row["growth_rate"]))
        self.assertTrue(math.isnan(row["cum_change_rate"]))
        self.assertFalse(bool(row["consecutive_decline"]))
        self.assertEqual(row["sample_size_flag"], "LOW")


def _make_metrics_row(
    dong: str,
    cat: str,
    *,
    cat_level: str = "MAJOR",
    growth: float,
    city_growth: float,
    cum: float,
    consecutive: bool,
    flag: str = "OK",
    store_count: int = 50,
) -> dict[str, object]:
    return {
        "dong_code": dong,
        "cat_code": cat,
        "period_id": "202606",
        "cat_level": cat_level,
        "store_count": store_count,
        "growth_rate": growth,
        "city_growth_rate": city_growth,
        "relative_gap": growth - city_growth,
        "cum_change_rate": cum,
        "consecutive_decline": consecutive,
        "sample_size_flag": flag,
    }


class ComputeScoresTest(unittest.TestCase):
    def test_grades_are_assigned_by_threshold_bands(self) -> None:
        # 인위적으로 서로 다른 RD/CUM/연속감소 조합을 만들어 점수 분포를 유도
        rows = [
            # rank 1 (가장 나쁨): RD/CUM 최하위 + 연속감소 → 최고점
            _make_metrics_row("D01", "A", growth=-0.30, city_growth=0.10, cum=-0.40, consecutive=True),
            # rank 2: 두 번째로 나쁨 + 연속감소
            _make_metrics_row("D02", "A", growth=-0.20, city_growth=0.10, cum=-0.30, consecutive=True),
            # rank 3: 감소는 있지만 연속감소 아님 → 중간
            _make_metrics_row("D03", "A", growth=-0.10, city_growth=0.10, cum=-0.20, consecutive=False),
            # rank 4: 대전과 비슷한 흐름 → 낮은 점수
            _make_metrics_row("D04", "A", growth=0.00, city_growth=0.10, cum=-0.05, consecutive=False),
            # rank 5: 오히려 대전보다 좋음 → 최저점
            _make_metrics_row("D05", "A", growth=0.15, city_growth=0.10, cum=0.10, consecutive=False),
        ]
        scored = compute_scores(pd.DataFrame(rows))

        # 점수는 rank 순서대로 단조 감소여야 함
        scores_by_dong = scored.set_index("dong_code")["score"]
        self.assertGreater(scores_by_dong["D01"], scores_by_dong["D02"])
        self.assertGreater(scores_by_dong["D02"], scores_by_dong["D03"])
        self.assertGreater(scores_by_dong["D03"], scores_by_dong["D04"])
        self.assertGreater(scores_by_dong["D04"], scores_by_dong["D05"])

        # 각 grade 밴드가 나오는지 (극단 케이스 검증)
        grades_by_dong = scored.set_index("dong_code")["grade"]
        # D01: P_rd=100, P_cum=100, C=100 → 0.5*100+0.3*100+0.2*100 = 100 → 중점검토
        self.assertEqual(grades_by_dong["D01"], "중점검토")
        # D05: P_rd=20, P_cum=20, C=0 → 0.5*20+0.3*20+0 = 16 → 정상
        self.assertEqual(grades_by_dong["D05"], "정상")

    def test_grade_boundary_scores_are_inclusive(self) -> None:
        # 임계값 정확히 걸리는 점수는 아래 등급으로 분류돼야 §1.5 검증 재현이 가능하다.
        self.assertEqual(_grade_for_score(80.0), "중점검토")
        self.assertEqual(_grade_for_score(79.999), "주의")
        self.assertEqual(_grade_for_score(65.0), "주의")
        self.assertEqual(_grade_for_score(64.999), "관심")
        self.assertEqual(_grade_for_score(50.0), "관심")
        self.assertEqual(_grade_for_score(49.999), "정상")
        self.assertIsNone(_grade_for_score(float("nan")))

    def test_low_sample_flag_rows_get_null_score_and_grade(self) -> None:
        rows = [
            _make_metrics_row("D01", "A", growth=-0.30, city_growth=0.10, cum=-0.40, consecutive=True),
            _make_metrics_row(
                "D02",
                "B",
                growth=-0.20,
                city_growth=0.05,
                cum=-0.25,
                consecutive=True,
                flag="LOW",
            ),
        ]
        scored = compute_scores(pd.DataFrame(rows))
        low_row = scored[scored["dong_code"] == "D02"].iloc[0]

        self.assertTrue(math.isnan(low_row["score"]))
        self.assertIsNone(low_row["grade"])

    def test_percentiles_are_scoped_within_cat_level(self) -> None:
        # MAJOR와 MIDDLE 풀이 섞이면 랭킹이 달라지므로 분리 계산 검증
        rows = [
            _make_metrics_row("D01", "M1", cat_level="MAJOR", growth=-0.10, city_growth=0.00, cum=-0.10, consecutive=False),
            _make_metrics_row("D02", "M2", cat_level="MAJOR", growth=0.05, city_growth=0.00, cum=0.05, consecutive=False),
            _make_metrics_row("D01", "S1", cat_level="MIDDLE", growth=-0.90, city_growth=0.00, cum=-0.90, consecutive=True),
            _make_metrics_row("D02", "S2", cat_level="MIDDLE", growth=0.80, city_growth=0.00, cum=0.80, consecutive=False),
        ]
        scored = compute_scores(pd.DataFrame(rows))

        # MIDDLE의 극단값이 MAJOR 점수에 영향을 주면 안 됨
        # MAJOR D01은 자기 풀 내 최하위 RD → P_rd=100
        major_d01 = scored[
            (scored["dong_code"] == "D01") & (scored["cat_level"] == "MAJOR")
        ].iloc[0]
        self.assertAlmostEqual(major_d01["score"], 0.5 * 100 + 0.3 * 100 + 0.2 * 0, places=6)

    def test_ok_row_with_nan_gap_produces_nan_score(self) -> None:
        # OK 표본이지만 이전 분기 결측으로 relative_gap이 NaN인 경우
        rows = [
            _make_metrics_row("D01", "A", growth=-0.10, city_growth=0.00, cum=-0.10, consecutive=False),
            {
                "dong_code": "D02",
                "cat_code": "A",
                "period_id": "202606",
                "cat_level": "MAJOR",
                "store_count": 30,
                "growth_rate": np.nan,
                "city_growth_rate": 0.0,
                "relative_gap": np.nan,
                "cum_change_rate": np.nan,
                "consecutive_decline": False,
                "sample_size_flag": "OK",
            },
        ]
        scored = compute_scores(pd.DataFrame(rows))
        nan_row = scored[scored["dong_code"] == "D02"].iloc[0]

        self.assertTrue(math.isnan(nan_row["score"]))
        self.assertIsNone(nan_row["grade"])


class ComputeDongScoresTest(unittest.TestCase):
    def test_uses_valid_major_top_three_and_anomaly_weight(self) -> None:
        rows = []
        for cat, score in (("A", 90.0), ("B", 70.0), ("C", 50.0), ("D", 10.0)):
            row = _make_metrics_row(
                "D01",
                cat,
                growth=0.0,
                city_growth=0.0,
                cum=0.0,
                consecutive=False,
            )
            row.update(score=score, grade="정상")
            rows.append(row)

        low = _make_metrics_row(
            "D01",
            "LOW",
            growth=-1.0,
            city_growth=0.0,
            cum=-1.0,
            consecutive=True,
            flag="LOW",
        )
        low.update(score=100.0, grade="중점검토")
        rows.append(low)

        middle = _make_metrics_row(
            "D01",
            "MID",
            cat_level="MIDDLE",
            growth=-1.0,
            city_growth=0.0,
            cum=-1.0,
            consecutive=True,
        )
        middle.update(score=100.0, grade="중점검토")
        rows.append(middle)

        result = compute_dong_scores(pd.DataFrame(rows))
        row = result.iloc[0]

        self.assertEqual(row["valid_cat_count"], 4)
        self.assertEqual(row["anomaly_cat_count"], 3)
        self.assertAlmostEqual(row["raw_score"], 70.0 * 0.9, places=6)
        self.assertEqual(row["pct_score"], 100.0)
        self.assertEqual(row["grade"], "중점검토")

    def test_percentile_and_grade_are_based_on_all_dong_raw_scores(self) -> None:
        rows = []
        for dong, score in (("D01", 10.0), ("D02", 20.0), ("D03", 30.0), ("D04", 40.0)):
            row = _make_metrics_row(
                dong,
                "A",
                growth=0.0,
                city_growth=0.0,
                cum=0.0,
                consecutive=False,
            )
            row.update(score=score, grade="정상")
            rows.append(row)

        result = compute_dong_scores(pd.DataFrame(rows)).set_index("dong_code")

        self.assertEqual(result.loc["D01", "pct_score"], 25.0)
        self.assertEqual(result.loc["D01", "grade"], "정상")
        self.assertEqual(result.loc["D02", "grade"], "관심")
        self.assertEqual(result.loc["D03", "grade"], "주의")
        self.assertEqual(result.loc["D04", "grade"], "중점검토")

    def test_dong_grade_boundaries_are_inclusive(self) -> None:
        self.assertEqual(_grade_for_dong_pct(90.0), "중점검토")
        self.assertEqual(_grade_for_dong_pct(70.0), "주의")
        self.assertEqual(_grade_for_dong_pct(40.0), "관심")
        self.assertEqual(_grade_for_dong_pct(39.999), "정상")
        self.assertIsNone(_grade_for_dong_pct(float("nan")))


if __name__ == "__main__":
    unittest.main()
