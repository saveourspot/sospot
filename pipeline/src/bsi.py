"""Load monthly BSI data and store it in long format."""

from __future__ import annotations

from pathlib import Path

import pandas as pd
from sqlalchemy import Engine, MetaData, Table
from sqlalchemy.dialects.postgresql import insert

from pipeline.src.db import get_engine


PROJECT_ROOT = Path(__file__).resolve().parents[2]
RAW_DATA_DIR = PROJECT_ROOT / "data" / "raw"
BSI_FILE_GLOB = "*BSI*현황_*.csv"
ID_COLUMNS = ("연도", "월")
FACT_COLUMNS = ("period_month", "metric_name", "value")
EXPECTED_METRIC_COUNT = 70


def discover_bsi_file(raw_dir: Path = RAW_DATA_DIR) -> Path:
    """Return the single BSI source file without guessing between versions."""
    files = sorted(raw_dir.glob(BSI_FILE_GLOB))
    if not files:
        raise FileNotFoundError(f"No BSI CSV file found in {raw_dir}")
    if len(files) > 1:
        names = ", ".join(path.name for path in files)
        raise ValueError(f"Multiple BSI CSV files found; select one explicitly: {names}")
    return files[0]


def load_bsi_source(path: Path | None = None) -> pd.DataFrame:
    """Load and structurally validate the cp949 BSI source."""
    source_path = discover_bsi_file() if path is None else path
    frame = pd.read_csv(source_path, encoding="cp949")

    missing_columns = [column for column in ID_COLUMNS if column not in frame.columns]
    if missing_columns:
        raise ValueError(f"BSI identifier columns are missing: {', '.join(missing_columns)}")

    metric_columns = [column for column in frame.columns if column not in ID_COLUMNS]
    if len(metric_columns) != EXPECTED_METRIC_COUNT:
        raise ValueError(
            f"Expected {EXPECTED_METRIC_COUNT} BSI metrics, found {len(metric_columns)}"
        )
    if len(metric_columns) != len(set(metric_columns)):
        raise ValueError("BSI metric names must be unique")
    if any(len(column) > 40 for column in metric_columns):
        raise ValueError("A BSI metric name exceeds the fact_bsi VARCHAR(40) limit")

    years = pd.to_numeric(frame["연도"], errors="coerce")
    months = pd.to_numeric(frame["월"], errors="coerce")
    if years.isna().any() or months.isna().any():
        raise ValueError("BSI year and month must be numeric")
    if (~months.between(1, 12)).any():
        raise ValueError("BSI month must be between 1 and 12")
    if frame.duplicated(subset=list(ID_COLUMNS)).any():
        raise ValueError("BSI source contains duplicate year-month rows")

    frame = frame.copy()
    frame["연도"] = years.astype(int)
    frame["월"] = months.astype(int)
    return frame


def transform_bsi(frame: pd.DataFrame) -> pd.DataFrame:
    """Convert the BSI wide table into monthly long-form facts."""
    metric_columns = [column for column in frame.columns if column not in ID_COLUMNS]
    period_month = (
        frame["연도"].astype(int).astype(str).str.zfill(4)
        + "-"
        + frame["월"].astype(int).astype(str).str.zfill(2)
    )
    source = frame.copy()
    source["period_month"] = period_month

    long_frame = source.melt(
        id_vars=["period_month"],
        value_vars=metric_columns,
        var_name="metric_name",
        value_name="value",
    )
    long_frame["value"] = pd.to_numeric(long_frame["value"], errors="coerce")
    if long_frame.duplicated(subset=["period_month", "metric_name"]).any():
        raise ValueError("BSI long data contains duplicate fact keys")
    return long_frame.loc[:, FACT_COLUMNS].sort_values(
        ["period_month", "metric_name"],
        ignore_index=True,
    )


def load_fact_bsi(
    facts: pd.DataFrame,
    engine: Engine | None = None,
) -> None:
    """Upsert BSI values so later source releases can fill prior missing values."""
    target_engine = engine or get_engine()
    table = Table("fact_bsi", MetaData(), autoload_with=target_engine)
    records = facts.astype(object).where(pd.notna(facts), None).to_dict(orient="records")
    if not records:
        return

    statement = insert(table).values(records)
    with target_engine.begin() as connection:
        connection.execute(
            statement.on_conflict_do_update(
                index_elements=["period_month", "metric_name"],
                set_={"value": statement.excluded.value},
            )
        )


def main() -> None:
    source = load_bsi_source()
    facts = transform_bsi(source)
    load_fact_bsi(facts)

    print(f"periods: {facts['period_month'].nunique()}")
    print(f"metrics: {facts['metric_name'].nunique()}")
    print(f"rows: {len(facts)}")


if __name__ == "__main__":
    main()
