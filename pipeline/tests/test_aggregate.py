from __future__ import annotations

import unittest

import pandas as pd

from pipeline.src.aggregate import aggregate_store_counts, build_fact_store_count


class AggregateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.frame = pd.DataFrame(
            [
                {
                    "행정동코드": "30110551",
                    "상권업종대분류코드": "I2",
                    "상권업종중분류코드": "I201",
                },
                {
                    "행정동코드": "30110551",
                    "상권업종대분류코드": "I2",
                    "상권업종중분류코드": "I201",
                },
                {
                    "행정동코드": "30110551",
                    "상권업종대분류코드": "I2",
                    "상권업종중분류코드": "I202",
                },
            ]
        )

    def test_aggregate_store_counts_builds_both_levels(self) -> None:
        facts = aggregate_store_counts(self.frame, "202503")

        major = facts[facts["cat_level"] == "MAJOR"]
        middle = facts[facts["cat_level"] == "MIDDLE"]
        self.assertEqual(int(major["store_count"].sum()), 3)
        self.assertEqual(int(middle["store_count"].sum()), 3)
        self.assertEqual(len(major), 1)
        self.assertEqual(len(middle), 2)

    def test_build_fact_store_count_preserves_period_order(self) -> None:
        facts = build_fact_store_count(
            {"202503": self.frame, "202506": self.frame.iloc[:1]}
        )

        self.assertEqual(facts["period_id"].drop_duplicates().tolist(), ["202503", "202506"])


if __name__ == "__main__":
    unittest.main()
