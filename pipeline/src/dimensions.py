"""Build and load SOSpot period and category dimensions."""

from __future__ import annotations

import calendar
import json
from collections.abc import Iterable
from datetime import date
from pathlib import Path
from typing import Any

import pandas as pd
from sqlalchemy import Engine, MetaData, Table
from sqlalchemy.dialects.postgresql import insert
from shapely.geometry import shape

from pipeline.src.db import get_engine
from pipeline.src.load import discover_shop_files, load_all
from pipeline.src.validate_inputs import period_index


PERIOD_COLUMNS = ("period_id", "year", "quarter", "base_date")
CATEGORY_COLUMNS = ("cat_code", "cat_name", "parent_code", "cat_level")
DONG_COLUMNS = ("dong_code", "sigungu", "dong_name", "center_lat", "center_lng")
PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_GEOJSON_PATH = PROJECT_ROOT / "data" / "geo" / "daejeon_dong.geojson"


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


def _build_dong_row(feature: dict[str, Any]) -> dict[str, object]:
    properties = feature.get("properties") or {}
    adm_cd2 = str(properties.get("adm_cd2", ""))
    dong_code = adm_cd2[:8]
    adm_name = str(properties.get("adm_nm", "")).strip()
    sigungu = str(properties.get("sggnm", "")).strip()

    if len(adm_cd2) < 8 or not dong_code.isdigit():
        raise ValueError(f"Invalid GeoJSON adm_cd2: {adm_cd2!r}")
    if properties.get("dong_code") != dong_code:
        raise ValueError(f"GeoJSON dong_code does not match adm_cd2: {dong_code}")
    if not adm_name or not sigungu:
        raise ValueError(f"GeoJSON administrative names are missing: {dong_code}")

    geometry = shape(feature.get("geometry"))
    if geometry.is_empty or geometry.geom_type not in {"Polygon", "MultiPolygon"}:
        raise ValueError(f"Invalid GeoJSON geometry for dong_code={dong_code}")
    centroid = geometry.centroid

    return {
        "dong_code": dong_code,
        "sigungu": sigungu,
        "dong_name": adm_name.split()[-1],
        "center_lat": centroid.y,
        "center_lng": centroid.x,
    }


def build_dim_dong(
    shop_frame: pd.DataFrame,
    geojson_path: Path = DEFAULT_GEOJSON_PATH,
) -> pd.DataFrame:
    """Build dong rows from GeoJSON and verify them against shop data."""
    with geojson_path.open(encoding="utf-8") as source:
        geojson = json.load(source)

    if geojson.get("type") != "FeatureCollection" or not isinstance(
        geojson.get("features"), list
    ):
        raise ValueError("Dong GeoJSON must be a FeatureCollection")

    dongs = pd.DataFrame(
        [_build_dong_row(feature) for feature in geojson["features"]],
        columns=DONG_COLUMNS,
    )
    duplicate_codes = dongs["dong_code"].duplicated(keep=False)
    if duplicate_codes.any():
        codes = ", ".join(dongs.loc[duplicate_codes, "dong_code"].astype(str))
        raise ValueError(f"GeoJSON contains duplicate dong codes: {codes}")

    _validate_category_mapping(
        shop_frame,
        "행정동코드",
        ["행정동명", "시군구명"],
    )
    shop_codes = set(shop_frame["행정동코드"].astype(str))
    geo_codes = set(dongs["dong_code"])
    if shop_codes != geo_codes:
        missing_geo = ", ".join(sorted(shop_codes - geo_codes)) or "none"
        missing_shop = ", ".join(sorted(geo_codes - shop_codes)) or "none"
        raise ValueError(
            "Dong codes do not match between shop data and GeoJSON: "
            f"missing_geo={missing_geo}; missing_shop={missing_shop}"
        )

    shop_names = (
        shop_frame[["행정동코드", "행정동명", "시군구명"]]
        .drop_duplicates()
        .set_index("행정동코드")
    )
    joined = dongs.join(shop_names, on="dong_code")
    name_mismatch = joined["dong_name"] != joined["행정동명"]
    sigungu_mismatch = joined["sigungu"] != joined["시군구명"]
    if name_mismatch.any() or sigungu_mismatch.any():
        codes = joined.loc[name_mismatch | sigungu_mismatch, "dong_code"]
        raise ValueError(
            "Administrative names do not match between shop data and GeoJSON: "
            + ", ".join(codes.astype(str))
        )

    return dongs.sort_values("dong_code", ignore_index=True)


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
    dongs: pd.DataFrame | None = None,
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
    if dongs is not None:
        _upsert_frame(target_engine, "dim_dong", dongs, "dong_code")
    _upsert_frame(
        target_engine,
        "dim_category",
        categories[categories["cat_level"] == "MIDDLE"],
        "cat_code",
    )


def main() -> None:
    periods = build_dim_period()
    shop_frames = load_all()
    combined_shops = pd.concat(shop_frames.values(), ignore_index=True)
    categories = build_dim_category(combined_shops)
    dongs = build_dim_dong(combined_shops)
    load_dimensions(periods, categories, dongs)

    counts = categories.groupby("cat_level").size()
    print(f"dim_period: {len(periods)}")
    print(f"dim_category MAJOR: {int(counts.get('MAJOR', 0))}")
    print(f"dim_category MIDDLE: {int(counts.get('MIDDLE', 0))}")
    print(f"dim_dong: {len(dongs)}")


if __name__ == "__main__":
    main()
