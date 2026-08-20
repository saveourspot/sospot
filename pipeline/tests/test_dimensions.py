from __future__ import annotations

import unittest
from datetime import date

import pandas as pd

from pipeline.src.dimensions import build_dim_category, build_dim_period


class DimensionsTest(unittest.TestCase):
    def test_build_dim_period_uses_discovered_quarters(self) -> None:
        periods = build_dim_period(["202412", "202503", "202506"])

        self.assertEqual(len(periods), 3)
        self.assertEqual(periods["quarter"].tolist(), [4, 1, 2])
        self.assertEqual(
            periods["base_date"].tolist(),
            [date(2024, 12, 31), date(2025, 3, 31), date(2025, 6, 30)],
        )

    def test_build_dim_category_builds_parent_relationship(self) -> None:
        frame = pd.DataFrame(
            [
                {
                    "상권업종대분류코드": "I2",
                    "상권업종대분류명": "음식",
                    "상권업종중분류코드": "I201",
                    "상권업종중분류명": "한식",
                },
                {
                    "상권업종대분류코드": "I2",
                    "상권업종대분류명": "음식",
                    "상권업종중분류코드": "I202",
                    "상권업종중분류명": "중식",
                },
            ]
        )

        categories = build_dim_category(frame)

        self.assertEqual(len(categories), 3)
        major = categories[categories["cat_level"] == "MAJOR"].iloc[0]
        self.assertEqual(major["cat_code"], "I2")
        self.assertIsNone(major["parent_code"])
        self.assertEqual(
            set(categories.loc[categories["cat_level"] == "MIDDLE", "parent_code"]),
            {"I2"},
        )

    def test_build_dim_category_rejects_conflicting_names(self) -> None:
        frame = pd.DataFrame(
            [
                {
                    "상권업종대분류코드": "I2",
                    "상권업종대분류명": "음식",
                    "상권업종중분류코드": "I201",
                    "상권업종중분류명": "한식",
                },
                {
                    "상권업종대분류코드": "I2",
                    "상권업종대분류명": "다른 이름",
                    "상권업종중분류코드": "I201",
                    "상권업종중분류명": "한식",
                },
            ]
        )

        with self.assertRaisesRegex(ValueError, "conflicting mappings"):
            build_dim_category(frame)


if __name__ == "__main__":
    unittest.main()
