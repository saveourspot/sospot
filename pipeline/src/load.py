"""Load and validate quarterly Daejeon commercial-area CSV files."""

from __future__ import annotations

import re
from pathlib import Path

import pandas as pd

from pipeline.src.validate_inputs import (
    EXPECTED_STORE_COUNTS,
    REQUIRED_SHOP_COLUMNS,
    validate_dataframe,
    validate_period_sequence,
)


PROJECT_ROOT = Path(__file__).resolve().parents[2]
RAW_DATA_DIR = PROJECT_ROOT / "data" / "raw"
SHOP_FILE_GLOB = "*상가(상권)정보_대전_*.csv"
SHOP_FILE_PATTERN = re.compile(r"_대전_(?P<period>\d{6})\.csv$")


def discover_shop_files(raw_dir: Path = RAW_DATA_DIR) -> dict[str, Path]:
    """Discover quarterly shop files and return them in chronological order."""
    discovered: dict[str, Path] = {}

    for path in raw_dir.glob(SHOP_FILE_GLOB):
        match = SHOP_FILE_PATTERN.search(path.name)
        if match is None:
            continue

        period = match.group("period")
        if period in discovered:
            raise ValueError(
                f"Duplicate shop data for period {period}: "
                f"{discovered[period].name}, {path.name}"
            )
        discovered[period] = path

    if not discovered:
        raise FileNotFoundError(f"No quarterly shop CSV files found in {raw_dir}")

    periods = validate_period_sequence(discovered)
    return {period: discovered[period] for period in periods}


def load_shop(period: str) -> pd.DataFrame:
    """Load the nine required columns for one quarterly period."""
    files = discover_shop_files()
    if period not in files:
        available = ", ".join(files)
        raise FileNotFoundError(
            f"Shop data for period {period} was not found. Available: {available}"
        )

    frame = pd.read_csv(
        files[period],
        encoding="utf-8",
        usecols=list(REQUIRED_SHOP_COLUMNS),
        dtype={"행정동코드": "string"},
    )
    frame["행정동코드"] = frame["행정동코드"].str.strip().str.zfill(8)
    validate_dataframe(frame, period)
    return frame


def load_all() -> dict[str, pd.DataFrame]:
    """Load every available quarterly file in chronological order."""
    return {period: load_shop(period) for period in discover_shop_files()}


def main() -> None:
    for period, frame in load_all().items():
        expected = EXPECTED_STORE_COUNTS.get(period)
        if expected is not None and len(frame) != expected:
            raise ValueError(
                f"{period} row count mismatch: expected {expected}, got {len(frame)}"
            )
        suffix = " ✓" if expected is not None else " (structure validated)"
        print(f"{period}: {len(frame)}{suffix}")


if __name__ == "__main__":
    main()
