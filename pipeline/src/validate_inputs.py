"""Shared input validation for SOSpot pipeline sources."""

from __future__ import annotations

import re
from collections.abc import Collection, Iterable

import pandas as pd


REQUIRED_SHOP_COLUMNS = (
    "행정동코드",
    "행정동명",
    "시군구명",
    "상권업종대분류코드",
    "상권업종대분류명",
    "상권업종중분류코드",
    "상권업종중분류명",
    "경도",
    "위도",
)

EXPECTED_STORE_COUNTS = {
    "202512": 78_246,
    "202603": 78_607,
    "202606": 80_704,
}

PERIOD_PATTERN = re.compile(r"^(?P<year>\d{4})(?P<month>03|06|09|12)$")


def period_index(period: str) -> int:
    """Convert a valid quarterly period into a monotonically increasing index."""
    match = PERIOD_PATTERN.fullmatch(period)
    if match is None:
        raise ValueError(
            f"Invalid period '{period}'; expected YYYYMM ending in 03, 06, 09, or 12"
        )

    year = int(match.group("year"))
    quarter = int(match.group("month")) // 3
    return year * 4 + quarter - 1


def validate_period_sequence(periods: Iterable[str]) -> list[str]:
    """Validate unique, chronological, consecutive quarterly periods."""
    period_list = list(periods)
    if len(period_list) != len(set(period_list)):
        raise ValueError("Duplicate periods are not allowed")

    ordered = sorted(period_list, key=period_index)
    indexes = [period_index(period) for period in ordered]
    for previous, current in zip(indexes, indexes[1:]):
        if current - previous != 1:
            raise ValueError("Shop data periods must be consecutive quarters")
    return ordered


def validate_required_columns(columns: Collection[str]) -> None:
    """Ensure all analysis columns exist in a source CSV."""
    missing = [column for column in REQUIRED_SHOP_COLUMNS if column not in columns]
    if missing:
        raise ValueError(f"Required shop columns are missing: {', '.join(missing)}")


def validate_dataframe(frame: pd.DataFrame, period: str) -> None:
    """Validate the shape and required values of a loaded quarterly frame."""
    period_index(period)
    validate_required_columns(frame.columns)

    missing_counts = frame.loc[:, REQUIRED_SHOP_COLUMNS].isna().sum()
    missing_counts = missing_counts[missing_counts > 0]
    if not missing_counts.empty:
        details = ", ".join(
            f"{column}={count}" for column, count in missing_counts.items()
        )
        raise ValueError(f"{period} contains missing required values: {details}")

    invalid_codes = ~frame["행정동코드"].astype(str).str.fullmatch(r"\d{8}")
    if invalid_codes.any():
        raise ValueError(
            f"{period} contains {int(invalid_codes.sum())} invalid dong codes"
        )
