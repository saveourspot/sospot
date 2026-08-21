from __future__ import annotations

import math
import unittest

import pandas as pd

from pipeline.src.metrics import (
    compute_combination_metrics,
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


if __name__ == "__main__":
    unittest.main()
