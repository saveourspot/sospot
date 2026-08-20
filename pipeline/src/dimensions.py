"""Build and load SOSpot period and category dimensions."""

from __future__ import annotations

import calendar
from collections.abc import Iterable
from datetime import date

import pandas as pd
from sqlalchemy import Engine, MetaData, Table
from sqlalchemy.dialects.postgresql import insert

from pipeline.src.db import get_engine
from pipeline.src.load import discover_shop_files, load_all
from pipeline.src.validate_inputs import period_index


PERIOD_COLUMNS = ("period_id", "year", "quarter", "base_date")
CATEGORY_COLUMNS = ("cat_code", "cat_name", "parent_code", "cat_level")


def build_dim_period(periods: Iterable[str] | None = None) -> pd.DataFrame:
    """Build period rows from dynamically discovered quarterly CSV files."""
    rows: list[dict[str, object]] = []
    source_periods = discover_shop_files() if periods is None else periods
    for period in source_periods:
        period_index(period)
        year = int(period[:4])
        month = int(period[4:])
        rows.append(
            {
                "period_id": period,
                "year": year,
                "quarter": month // 3,
                "base_date": date(year, month, calendar.monthrange(year, month)[1]),
            }
        )
    return pd.DataFrame(rows, columns=PERIOD_COLUMNS)


def _validate_category_mapping(
    frame: pd.DataFrame,
    code_column: str,
    mapped_columns: list[str],
) -> None:
    mapping_counts = frame.groupby(code_column, dropna=False)[mapped_columns].nunique()
    conflicts = mapping_counts.gt(1).any(axis=1)
    if conflicts.any():
        codes = ", ".join(mapping_counts.index[conflicts].astype(str))
        raise ValueError(f"Category codes have conflicting mappings: {codes}")


def build_dim_category(frame: pd.DataFrame) -> pd.DataFrame:
    """Build unique major and middle category rows from loaded shop data."""
    _validate_category_mapping(
        frame,
        "상권업종대분류코드",
        ["상권업종대분류명"],
    )
    _validate_category_mapping(
        frame,
        "상권업종중분류코드",
        ["상권업종중분류명", "상권업종대분류코드"],
    )

    major = (
        frame[["상권업종대분류코드", "상권업종대분류명"]]
        .drop_duplicates()
        .rename(
            columns={
                "상권업종대분류코드": "cat_code",
                "상권업종대분류명": "cat_name",
            }
        )
        .assign(parent_code=None, cat_level="MAJOR")
    )
    middle = (
        frame[
            [
                "상권업종중분류코드",
                "상권업종중분류명",
                "상권업종대분류코드",
            ]
        ]
        .drop_duplicates()
        .rename(
            columns={
                "상권업종중분류코드": "cat_code",
                "상권업종중분류명": "cat_name",
                "상권업종대분류코드": "parent_code",
            }
        )
        .assign(cat_level="MIDDLE")
    )

    categories = pd.concat([major, middle], ignore_index=True)
    duplicate_codes = categories["cat_code"].duplicated(keep=False)
    if duplicate_codes.any():
        codes = ", ".join(categories.loc[duplicate_codes, "cat_code"].astype(str))
        raise ValueError(f"Category codes occur at multiple levels: {codes}")

    return categories.loc[:, CATEGORY_COLUMNS].sort_values(
        ["cat_level", "cat_code"],
        key=lambda values: values.map({"MAJOR": 0, "MIDDLE": 1}).fillna(values),
        ignore_index=True,
    )


def _upsert_frame(
    engine: Engine,
    table_name: str,
    frame: pd.DataFrame,
    key_column: str,
) -> None:
    records = frame.to_dict(orient="records")
    if not records:
        return

    table = Table(table_name, MetaData(), autoload_with=engine)
    statement = insert(table).values(records)
    update_columns = {
        column.name: statement.excluded[column.name]
        for column in table.columns
        if column.name != key_column
    }
    with engine.begin() as connection:
        connection.execute(
            statement.on_conflict_do_update(
                index_elements=[key_column],
                set_=update_columns,
            )
        )


def load_dimensions(
    periods: pd.DataFrame,
    categories: pd.DataFrame,
    engine: Engine | None = None,
) -> None:
    """Upsert period and category dimensions in foreign-key-safe order."""
    target_engine = engine or get_engine()
    _upsert_frame(target_engine, "dim_period", periods, "period_id")
    _upsert_frame(
        target_engine,
        "dim_category",
        categories[categories["cat_level"] == "MAJOR"],
        "cat_code",
    )
    _upsert_frame(
        target_engine,
        "dim_category",
        categories[categories["cat_level"] == "MIDDLE"],
        "cat_code",
    )


def main() -> None:
    periods = build_dim_period()
    shop_frames = load_all()
    categories = build_dim_category(
        pd.concat(shop_frames.values(), ignore_index=True)
    )
    load_dimensions(periods, categories)

    counts = categories.groupby("cat_level").size()
    print(f"dim_period: {len(periods)}")
    print(f"dim_category MAJOR: {int(counts.get('MAJOR', 0))}")
    print(f"dim_category MIDDLE: {int(counts.get('MIDDLE', 0))}")


if __name__ == "__main__":
    main()
