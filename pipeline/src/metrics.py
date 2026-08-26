"""Compute combination-level anomaly metrics from fact_store_count."""

from __future__ import annotations

from typing import Iterable

import numpy as np
import pandas as pd
from sqlalchemy import Engine, MetaData, Table
from sqlalchemy.dialects.postgresql import insert

from pipeline.src.db import get_engine


SAMPLE_SIZE_THRESHOLD = 20

SCORE_WEIGHT_RD = 0.5
SCORE_WEIGHT_CUM = 0.3
SCORE_WEIGHT_CONSECUTIVE = 0.2

GRADE_THRESHOLDS = (
    (80.0, "중점검토"),
    (65.0, "주의"),
    (50.0, "관심"),
)
GRADE_DEFAULT = "정상"

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

FACT_ANOMALY_COLUMNS = (
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
    "score",
    "grade",
)

DONG_GRADE_THRESHOLDS = (
    (90.0, "중점검토"),
    (70.0, "주의"),
    (40.0, "관심"),
)

FACT_DONG_SCORE_COLUMNS = (
    "dong_code",
    "period_id",
    "raw_score",
    "pct_score",
    "grade",
    "anomaly_cat_count",
    "valid_cat_count",
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


def compute_scores(metrics: pd.DataFrame) -> pd.DataFrame:
    """Add score and grade columns using per cat_level percentile pools.

    Percentiles are computed only over `sample_size_flag='OK'` rows within each
    cat_level (MAJOR pool separate from MIDDLE). Rows with NaN inputs (e.g.
    missing prior-period store counts even under OK) remain NaN and therefore
    receive NaN score and NULL grade, matching the LOW handling convention.
    """
    scored = metrics.copy()
    scored["score"] = np.nan
    scored["grade"] = pd.Series([None] * len(scored), dtype=object)

    for cat_level, level_frame in scored.groupby("cat_level", sort=False):
        ok_mask = level_frame["sample_size_flag"] == "OK"
        ok_frame = level_frame[ok_mask]
        if ok_frame.empty:
            continue

        p_rd = (-ok_frame["relative_gap"]).rank(pct=True) * 100
        p_cum = (-ok_frame["cum_change_rate"]).rank(pct=True) * 100
        consecutive_component = ok_frame["consecutive_decline"].astype(float) * 100

        score = (
            SCORE_WEIGHT_RD * p_rd
            + SCORE_WEIGHT_CUM * p_cum
            + SCORE_WEIGHT_CONSECUTIVE * consecutive_component
        )

        scored.loc[score.index, "score"] = score
        scored.loc[score.index, "grade"] = score.map(_grade_for_score)

    return scored


def _grade_for_score(score: float) -> str | None:
    if pd.isna(score):
        return None
    for threshold, grade in GRADE_THRESHOLDS:
        if score >= threshold:
            return grade
    return GRADE_DEFAULT


def load_fact_anomaly(
    scored: pd.DataFrame,
    engine: Engine | None = None,
) -> None:
    """Upsert scored combinations into fact_anomaly so re-runs stay idempotent."""
    target_engine = engine or get_engine()
    table = Table("fact_anomaly", MetaData(), autoload_with=target_engine)
    payload = scored.loc[:, list(FACT_ANOMALY_COLUMNS)]
    records = (
        payload.astype(object).where(pd.notna(payload), None).to_dict(orient="records")
    )
    if not records:
        return

    statement = insert(table).values(records)
    update_columns = {
        column: statement.excluded[column]
        for column in FACT_ANOMALY_COLUMNS
        if column not in ("dong_code", "cat_code", "period_id")
    }
    with target_engine.begin() as connection:
        connection.execute(
            statement.on_conflict_do_update(
                index_elements=["dong_code", "cat_code", "period_id"],
                set_=update_columns,
            )
        )


def compute_dong_scores(scored: pd.DataFrame) -> pd.DataFrame:
    """Aggregate valid MAJOR-category scores into one composite score per dong."""
    major = scored[scored["cat_level"] == "MAJOR"]
    periods = major["period_id"].drop_duplicates().tolist()
    if len(periods) != 1:
        raise ValueError("Dong scores must be computed for exactly one target period")

    valid = major[
        (major["sample_size_flag"] == "OK") & major["score"].notna()
    ].copy()
    if valid.empty:
        return pd.DataFrame(columns=FACT_DONG_SCORE_COLUMNS)

    counts = valid.groupby("dong_code").agg(
        valid_cat_count=("cat_code", "count"),
        anomaly_cat_count=("score", lambda values: int((values >= 50.0).sum())),
    )
    top_average = (
        valid.sort_values(
            ["dong_code", "score"], ascending=[True, False], kind="stable"
        )
        .groupby("dong_code")
        .head(3)
        .groupby("dong_code")["score"]
        .mean()
        .rename("top_average")
    )

    dong_scores = counts.join(top_average).reset_index()
    weight = np.minimum(1.0, 0.6 + 0.1 * dong_scores["anomaly_cat_count"])
    dong_scores["raw_score"] = dong_scores["top_average"] * weight
    dong_scores["pct_score"] = dong_scores["raw_score"].rank(pct=True) * 100
    dong_scores["grade"] = dong_scores["pct_score"].map(_grade_for_dong_pct)
    dong_scores["period_id"] = periods[0]

    dong_scores[["valid_cat_count", "anomaly_cat_count"]] = dong_scores[
        ["valid_cat_count", "anomaly_cat_count"]
    ].astype(int)
    return dong_scores.loc[:, list(FACT_DONG_SCORE_COLUMNS)]


def _grade_for_dong_pct(pct_score: float) -> str | None:
    if pd.isna(pct_score):
        return None
    for threshold, grade in DONG_GRADE_THRESHOLDS:
        if pct_score >= threshold:
            return grade
    return GRADE_DEFAULT


def load_fact_dong_score(
    dong_scores: pd.DataFrame,
    engine: Engine | None = None,
) -> None:
    """Upsert dong composite scores so period snapshots are preserved on re-runs."""
    target_engine = engine or get_engine()
    table = Table("fact_dong_score", MetaData(), autoload_with=target_engine)
    payload = dong_scores.loc[:, list(FACT_DONG_SCORE_COLUMNS)]
    records = (
        payload.astype(object).where(pd.notna(payload), None).to_dict(orient="records")
    )
    if not records:
        return

    statement = insert(table).values(records)
    update_columns = {
        column: statement.excluded[column]
        for column in FACT_DONG_SCORE_COLUMNS
        if column not in ("dong_code", "period_id")
    }
    with target_engine.begin() as connection:
        connection.execute(
            statement.on_conflict_do_update(
                index_elements=["dong_code", "period_id"],
                set_=update_columns,
            )
        )


def main() -> None:
    counts = load_fact_store_count()
    analyzable = determine_analyzable_periods(counts)
    if not analyzable:
        print("No analyzable periods (need three consecutive quarters).")
        return

    engine = get_engine()
    for target_period in analyzable:
        metrics = compute_combination_metrics(counts, target_period)
        scored = compute_scores(metrics)
        load_fact_anomaly(scored, engine=engine)
        dong_scores = compute_dong_scores(scored)
        load_fact_dong_score(dong_scores, engine=engine)

        for cat_level in ("MAJOR", "MIDDLE"):
            level_frame = scored[scored["cat_level"] == cat_level]
            ok = int((level_frame["sample_size_flag"] == "OK").sum())
            low = int((level_frame["sample_size_flag"] == "LOW").sum())
            total = ok + low
            grade_counts = (
                level_frame[level_frame["sample_size_flag"] == "OK"]["grade"]
                .value_counts()
                .to_dict()
            )
            grade_str = " · ".join(
                f"{grade} {grade_counts.get(grade, 0)}"
                for _, grade in GRADE_THRESHOLDS
            )
            grade_str += f" · {GRADE_DEFAULT} {grade_counts.get(GRADE_DEFAULT, 0)}"
            print(
                f"{target_period} {cat_level}: total {total} · OK {ok} · LOW {low} "
                f"| {grade_str}"
            )

        dong_grade_counts = dong_scores["grade"].value_counts().to_dict()
        dong_grade_str = " · ".join(
            f"{grade} {dong_grade_counts.get(grade, 0)}"
            for _, grade in DONG_GRADE_THRESHOLDS
        )
        dong_grade_str += (
            f" · {GRADE_DEFAULT} {dong_grade_counts.get(GRADE_DEFAULT, 0)}"
        )
        print(f"{target_period} DONG: total {len(dong_scores)} | {dong_grade_str}")


if __name__ == "__main__":
    main()
