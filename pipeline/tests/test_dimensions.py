from __future__ import annotations

import unittest
from datetime import date

import pandas as pd

from pipeline.src.dimensions import (
    _build_dong_row,
    build_dim_category,
    build_dim_period,
)


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

    def test_build_dong_row_calculates_multipolygon_centroid(self) -> None:
        feature = {
            "type": "Feature",
            "properties": {
                "adm_cd2": "3011055100",
                "adm_nm": "대전광역시 동구 판암1동",
                "sggnm": "동구",
                "dong_code": "30110551",
            },
            "geometry": {
                "type": "MultiPolygon",
                "coordinates": [
                    [
                        [
                            [127.0, 36.0],
                            [128.0, 36.0],
                            [128.0, 37.0],
                            [127.0, 37.0],
                            [127.0, 36.0],
                        ]
                    ]
                ],
            },
        }

        dong = _build_dong_row(feature)

        self.assertEqual(dong["dong_code"], "30110551")
        self.assertEqual(dong["sigungu"], "동구")
        self.assertEqual(dong["dong_name"], "판암1동")
        self.assertAlmostEqual(dong["center_lat"], 36.5)
        self.assertAlmostEqual(dong["center_lng"], 127.5)


if __name__ == "__main__":
    unittest.main()
