"""Aggregate quarterly store counts at major and middle category levels."""

from __future__ import annotations

import pandas as pd
from sqlalchemy import Engine, MetaData, Table, select

from pipeline.src.db import get_engine
from pipeline.src.load import load_all


FACT_COLUMNS = ("dong_code", "cat_code", "period_id", "cat_level", "store_count")


def _aggregate_level(
    frame: pd.DataFrame,
    period: str,
    category_column: str,
    cat_level: str,
) -> pd.DataFrame:
    aggregated = (
        frame.groupby(["행정동코드", category_column], observed=True)
        .size()
        .rename("store_count")
        .reset_index()
        .rename(
            columns={
                "행정동코드": "dong_code",
                category_column: "cat_code",
            }
        )
        .assign(period_id=period, cat_level=cat_level)
    )
    return aggregated.loc[:, FACT_COLUMNS]


def aggregate_store_counts(frame: pd.DataFrame, period: str) -> pd.DataFrame:
    """Aggregate one quarter at both supported category levels."""
    major = _aggregate_level(
        frame,
        period,
        "상권업종대분류코드",
        "MAJOR",
    )
    middle = _aggregate_level(
        frame,
        period,
        "상권업종중분류코드",
        "MIDDLE",
    )

    expected_total = len(frame)
    for cat_level, aggregated in (("MAJOR", major), ("MIDDLE", middle)):
        actual_total = int(aggregated["store_count"].sum())
        if actual_total != expected_total:
            raise ValueError(
                f"{period} {cat_level} aggregate mismatch: "
                f"expected {expected_total}, got {actual_total}"
            )

    combined = pd.concat([major, middle], ignore_index=True)
    duplicate_keys = combined.duplicated(
        subset=["dong_code", "cat_code", "period_id"],
        keep=False,
    )
    if duplicate_keys.any():
        raise ValueError(f"{period} aggregate contains duplicate fact keys")
    return combined


def build_fact_store_count(
    shop_frames: dict[str, pd.DataFrame] | None = None,
) -> pd.DataFrame:
    """Build fact rows for every dynamically discovered shop period."""
    frames = load_all() if shop_frames is None else shop_frames
    facts = [
        aggregate_store_counts(frame, period)
        for period, frame in frames.items()
    ]
    if not facts:
        return pd.DataFrame(columns=FACT_COLUMNS)
    return pd.concat(facts, ignore_index=True)


def load_fact_store_count(
    facts: pd.DataFrame,
    engine: Engine | None = None,
) -> list[str]:
    """Append only periods that do not yet have a stored snapshot."""
    target_engine = engine or get_engine()
    table = Table("fact_store_count", MetaData(), autoload_with=target_engine)

    with target_engine.begin() as connection:
        existing_periods = set(
            connection.execute(select(table.c.period_id).distinct()).scalars()
        )
        available_periods = list(dict.fromkeys(facts["period_id"].astype(str)))
        new_periods = [period for period in available_periods if period not in existing_periods]

        if not new_periods:
            return []

        new_facts = facts[facts["period_id"].isin(new_periods)]
        connection.execute(table.insert(), new_facts.to_dict(orient="records"))
    return new_periods


def main() -> None:
    facts = build_fact_store_count()
    inserted_periods = load_fact_store_count(facts)

    for period, period_facts in facts.groupby("period_id", sort=True):
        major_total = int(
            period_facts.loc[
                period_facts["cat_level"] == "MAJOR", "store_count"
            ].sum()
        )
        status = "inserted" if period in inserted_periods else "already stored"
        print(f"{period}: {major_total} ({status})")


if __name__ == "__main__":
    main()
