from __future__ import annotations

import unittest

import pandas as pd

from pipeline.src.validate_inputs import (
    REQUIRED_SHOP_COLUMNS,
    validate_dataframe,
    validate_period_sequence,
    validate_required_columns,
)


class ValidateInputsTest(unittest.TestCase):
    def test_period_sequence_is_sorted_chronologically(self) -> None:
        self.assertEqual(
            validate_period_sequence(["202606", "202512", "202603"]),
            ["202512", "202603", "202606"],
        )

    def test_period_sequence_rejects_gap(self) -> None:
        with self.assertRaisesRegex(ValueError, "consecutive"):
            validate_period_sequence(["202512", "202606"])

    def test_period_sequence_rejects_invalid_month(self) -> None:
        with self.assertRaisesRegex(ValueError, "Invalid period"):
            validate_period_sequence(["202605"])

    def test_required_columns_reports_missing_column(self) -> None:
        with self.assertRaisesRegex(ValueError, "위도"):
            validate_required_columns(REQUIRED_SHOP_COLUMNS[:-1])

    def test_dataframe_rejects_missing_required_value(self) -> None:
        row = {column: "value" for column in REQUIRED_SHOP_COLUMNS}
        row["행정동코드"] = "30110551"
        row["위도"] = None

        with self.assertRaisesRegex(ValueError, "missing required values"):
            validate_dataframe(pd.DataFrame([row]), "202606")

    def test_dataframe_rejects_invalid_dong_code(self) -> None:
        row = {column: "value" for column in REQUIRED_SHOP_COLUMNS}
        row["행정동코드"] = "3011055"

        with self.assertRaisesRegex(ValueError, "invalid dong codes"):
            validate_dataframe(pd.DataFrame([row]), "202606")


if __name__ == "__main__":
    unittest.main()
