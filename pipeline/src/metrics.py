"""Compute combination-level anomaly metrics from fact_store_count."""

from __future__ import annotations

from typing import Iterable

import numpy as np
import pandas as pd
from sqlalchemy import Engine

from pipeline.src.db import get_engine


SAMPLE_SIZE_THRESHOLD = 20

METRIC_COLUMNS = (
    "dong_code",
    "cat_code",
    "period_id",
    "cat_level",
    "store_count",
    "growth_rate",
    "city_growth_rate",
    "relative_gap",
    "cum_change_rate",
    "consecutive_decline",
    "sample_size_flag",
)


def load_fact_store_count(engine: Engine | None = None) -> pd.DataFrame:
    """Read the entire fact_store_count table into a DataFrame."""
    active_engine = engine or get_engine()
    query = "SELECT dong_code, cat_code, period_id, cat_level, store_count FROM fact_store_count"
    return pd.read_sql(query, active_engine)


def determine_analyzable_periods(counts: pd.DataFrame) -> list[str]:
    """Return target periods `t` for which `t-2`, `t-1`, `t` all exist and are consecutive."""
    periods = sorted(counts["period_id"].drop_duplicates().tolist())
    analyzable: list[str] = []
    for index in range(2, len(periods)):
        window = periods[index - 2 : index + 1]
        if _are_consecutive_quarters(window):
            analyzable.append(window[-1])
    return analyzable


def _are_consecutive_quarters(periods: Iterable[str]) -> bool:
    ordered = list(periods)
    for previous, current in zip(ordered, ordered[1:]):
        if _next_quarter(previous) != current:
            return False
    return True


def _next_quarter(period_id: str) -> str:
    year = int(period_id[:4])
    month = int(period_id[4:])
    quarter = (month - 1) // 3 + 1
    next_quarter = quarter + 1
    next_year = year
    if next_quarter > 4:
        next_quarter = 1
        next_year += 1
    next_month = next_quarter * 3
    return f"{next_year}{next_month:02d}"


def _preceding_periods(counts: pd.DataFrame, target_period: str) -> tuple[str, str]:
    periods = sorted(counts["period_id"].drop_duplicates().tolist())
    if target_period not in periods:
        raise ValueError(f"Target period {target_period} not present in fact_store_count")
    index = periods.index(target_period)
    if index < 2:
        raise ValueError(
            f"Target period {target_period} lacks two preceding periods in fact_store_count"
        )
    return periods[index - 2], periods[index - 1]


def compute_combination_metrics(counts: pd.DataFrame, target_period: str) -> pd.DataFrame:
    """Compute g, G, RD, CUM, C and sample_size_flag for each (dong, cat) combination at t."""
    prev2_period, prev1_period = _preceding_periods(counts, target_period)

    frames: list[pd.DataFrame] = []
    for cat_level in ("MAJOR", "MIDDLE"):
        frames.append(
            _compute_level(counts, cat_level, target_period, prev1_period, prev2_period)
        )
    return pd.concat(frames, ignore_index=True)


def _compute_level(
    counts: pd.DataFrame,
    cat_level: str,
    target_period: str,
    prev1_period: str,
    prev2_period: str,
) -> pd.DataFrame:
    level_frame = counts[counts["cat_level"] == cat_level]

    target = level_frame[level_frame["period_id"] == target_period][
        ["dong_code", "cat_code", "store_count"]
    ].rename(columns={"store_count": "n_t"})

    prev1 = level_frame[level_frame["period_id"] == prev1_period][
        ["dong_code", "cat_code", "store_count"]
    ].rename(columns={"store_count": "n_t1"})

    prev2 = level_frame[level_frame["period_id"] == prev2_period][
        ["dong_code", "cat_code", "store_count"]
    ].rename(columns={"store_count": "n_t2"})

    merged = (
        target.merge(prev1, on=["dong_code", "cat_code"], how="left")
        .merge(prev2, on=["dong_code", "cat_code"], how="left")
        .fillna({"n_t1": 0, "n_t2": 0})
    )
    merged[["n_t1", "n_t2"]] = merged[["n_t1", "n_t2"]].astype(int)

    merged["growth_rate"] = _safe_ratio(merged["n_t"] - merged["n_t1"], merged["n_t1"])
    merged["prev_growth_rate"] = _safe_ratio(
        merged["n_t1"] - merged["n_t2"], merged["n_t2"]
    )
    merged["cum_change_rate"] = _safe_ratio(merged["n_t"] - merged["n_t2"], merged["n_t2"])

    city_totals = _city_totals(level_frame, target_period, prev1_period)
    merged = merged.merge(city_totals, on="cat_code", how="left")
    merged["city_growth_rate"] = _safe_ratio(
        merged["city_n_t"] - merged["city_n_t1"], merged["city_n_t1"]
    )

    merged["relative_gap"] = merged["growth_rate"] - merged["city_growth_rate"]

    consecutive = (merged["growth_rate"] < 0) & (merged["prev_growth_rate"] < 0)
    merged["consecutive_decline"] = consecutive.fillna(False).astype(bool)

    merged["sample_size_flag"] = np.where(
        merged["n_t2"] >= SAMPLE_SIZE_THRESHOLD, "OK", "LOW"
    )

    merged["period_id"] = target_period
    merged["cat_level"] = cat_level
    merged = merged.rename(columns={"n_t": "store_count"})

    return merged.loc[:, list(METRIC_COLUMNS)]


def _safe_ratio(numerator: pd.Series, denominator: pd.Series) -> pd.Series:
    numerator = numerator.astype(float)
    denominator = denominator.astype(float)
    ratio = numerator.divide(denominator).where(denominator != 0, np.nan)
    return ratio


def _city_totals(
    level_frame: pd.DataFrame, target_period: str, prev1_period: str
) -> pd.DataFrame:
    def sum_by_cat(period: str, column: str) -> pd.DataFrame:
        return (
            level_frame[level_frame["period_id"] == period]
            .groupby("cat_code", as_index=False)["store_count"]
            .sum()
            .rename(columns={"store_count": column})
        )

    return (
        sum_by_cat(target_period, "city_n_t")
        .merge(sum_by_cat(prev1_period, "city_n_t1"), on="cat_code", how="outer")
        .fillna(0)
    )


def main() -> None:
    counts = load_fact_store_count()
    analyzable = determine_analyzable_periods(counts)
    if not analyzable:
        print("No analyzable periods (need three consecutive quarters).")
        return

    for target_period in analyzable:
        metrics = compute_combination_metrics(counts, target_period)
        summary = (
            metrics.groupby(["cat_level", "sample_size_flag"], observed=True)
            .size()
            .unstack(fill_value=0)
        )
        for cat_level in ("MAJOR", "MIDDLE"):
            ok = int(summary.loc[cat_level, "OK"]) if cat_level in summary.index else 0
            low = int(summary.loc[cat_level, "LOW"]) if cat_level in summary.index else 0
            total = ok + low
            print(f"{target_period} {cat_level}: total {total} · OK {ok} · LOW {low}")


if __name__ == "__main__":
    main()
