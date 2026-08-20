from __future__ import annotations

import unittest

import pandas as pd

from pipeline.src.bsi import transform_bsi


class BsiTest(unittest.TestCase):
    def test_transform_bsi_builds_period_month_and_long_rows(self) -> None:
        source = pd.DataFrame(
            [
                {"연도": 2026, "월": 4, "대전체감": 56.4, "음식점업체감": 70.0},
                {"연도": 2026, "월": 5, "대전체감": 63.6, "음식점업체감": 72.0},
            ]
        )

        facts = transform_bsi(source)

        self.assertEqual(len(facts), 4)
        self.assertEqual(set(facts["period_month"]), {"2026-04", "2026-05"})
        self.assertEqual(set(facts["metric_name"]), {"대전체감", "음식점업체감"})

    def test_transform_bsi_preserves_missing_value(self) -> None:
        source = pd.DataFrame([{"연도": 2026, "월": 6, "대전체감": None}])

        facts = transform_bsi(source)

        self.assertTrue(pd.isna(facts.iloc[0]["value"]))


if __name__ == "__main__":
    unittest.main()
