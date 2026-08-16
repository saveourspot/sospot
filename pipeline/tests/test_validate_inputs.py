import json
import sys
import tempfile
import unittest
from pathlib import Path

import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

import validate_inputs as validator


class ValidateInputsTest(unittest.TestCase):
    def _frame(self, dong_codes=("1", "2")):
        return pd.DataFrame(
            {
                "행정동코드": dong_codes,
                "행정동명": [f"동{code}" for code in dong_codes],
                "시군구명": ["중구"] * len(dong_codes),
                "상권업종대분류코드": ["I1"] * len(dong_codes),
                "상권업종중분류코드": ["I101"] * len(dong_codes),
                "경도": [127.0] * len(dong_codes),
                "위도": [36.0] * len(dong_codes),
            }
        )

    def test_validate_inputs_accepts_matching_fixture(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            raw = root / "raw"
            raw.mkdir()
            for period in ("202503", "202506", "202509"):
                self._frame().to_csv(raw / f"stores_{period}.csv", index=False)
            geojson = root / "daejeon_dong.geojson"
            geojson.write_text(
                json.dumps(
                    {
                        "type": "FeatureCollection",
                        "features": [
                            {
                                "type": "Feature",
                                "properties": {
                                    "행정동코드": code,
                                    "sidonm": "대전광역시",
                                },
                                "geometry": {
                                    "type": "Polygon",
                                    "coordinates": [],
                                },
                            }
                            for code in ("1", "2")
                        ],
                    }
                ),
                encoding="utf-8",
            )

            original = (
                validator.EXPECTED_DONG_COUNT,
                validator.EXPECTED_MAJOR_CATEGORY_COUNT,
                validator.EXPECTED_MIDDLE_CATEGORY_COUNT,
            )
            validator.EXPECTED_DONG_COUNT = 2
            validator.EXPECTED_MAJOR_CATEGORY_COUNT = 1
            validator.EXPECTED_MIDDLE_CATEGORY_COUNT = 1
            try:
                summaries = validator.validate_inputs(raw, geojson)
            finally:
                (
                    validator.EXPECTED_DONG_COUNT,
                    validator.EXPECTED_MAJOR_CATEGORY_COUNT,
                    validator.EXPECTED_MIDDLE_CATEGORY_COUNT,
                ) = original

            self.assertEqual([item.period for item in summaries], ["202503", "202506", "202509"])

    def test_rejects_missing_required_column(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "stores_202503.csv"
            self._frame().drop(columns="위도").to_csv(path, index=False)
            with self.assertRaisesRegex(validator.ValidationError, "필수 컬럼 누락"):
                validator.validate_csv(path)

    def test_rejects_nonconsecutive_periods(self):
        summaries = [
            validator.CsvSummary(Path("a"), "202503", 0, frozenset(), 0, 0),
            validator.CsvSummary(Path("b"), "202509", 0, frozenset(), 0, 0),
        ]
        with self.assertRaisesRegex(validator.ValidationError, "연속되지 않습니다"):
            validator.validate_periods(summaries)

    def test_rejects_geo_code_mismatch(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "dong.geojson"
            path.write_text(
                json.dumps(
                    {
                        "type": "FeatureCollection",
                        "features": [
                            {
                                "type": "Feature",
                                "properties": {
                                    "행정동코드": "1",
                                    "sidonm": "대전광역시",
                                },
                                "geometry": {
                                    "type": "Polygon",
                                    "coordinates": [],
                                },
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(validator.ValidationError, "코드 불일치"):
                validator.validate_geojson(path, frozenset({"1", "2"}))

    def test_uses_first_eight_digits_of_adm_cd2(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "dong.geojson"
            path.write_text(
                json.dumps(
                    {
                        "type": "FeatureCollection",
                        "features": [
                            {
                                "type": "Feature",
                                "properties": {
                                    "adm_cd": "25010530",
                                    "adm_cd2": "3011053000",
                                    "sidonm": "대전광역시",
                                },
                                "geometry": {
                                    "type": "MultiPolygon",
                                    "coordinates": [],
                                },
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            selected = validator.validate_geojson(path, frozenset({"30110530"}))
            self.assertEqual(selected, "adm_cd2")


if __name__ == "__main__":
    unittest.main()
