"""Validate SOSpot raw store CSVs and Daejeon dong GeoJSON before analysis."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path

import pandas as pd


REQUIRED_COLUMNS = {
    "행정동코드",
    "행정동명",
    "시군구명",
    "상권업종대분류코드",
    "상권업종중분류코드",
    "경도",
    "위도",
}
EXPECTED_COUNTS = {
    "202512": 78_246,
    "202603": 78_607,
    "202606": 80_704,
}
EXPECTED_DONG_COUNT = 82
EXPECTED_MAJOR_CATEGORY_COUNT = 10
EXPECTED_MIDDLE_CATEGORY_COUNT = 74
PERIOD_PATTERN = re.compile(r"(?<!\d)(20\d{2}(?:03|06|09|12))(?!\d)")
GEO_CODE_CANDIDATES = ("행정동코드", "adm_cd2", "adm_cd", "ADM_CD")


class ValidationError(Exception):
    """Raised when input data does not satisfy the project requirements."""


@dataclass(frozen=True)
class CsvSummary:
    path: Path
    period: str
    row_count: int
    dong_codes: frozenset[str]
    major_category_count: int
    middle_category_count: int


def _read_csv(path: Path) -> pd.DataFrame:
    errors: list[str] = []
    for encoding in ("utf-8-sig", "cp949"):
        try:
            return pd.read_csv(path, encoding=encoding, low_memory=False)
        except UnicodeDecodeError as exc:
            errors.append(f"{encoding}: {exc}")
    raise ValidationError(f"CSV 인코딩을 확인할 수 없습니다: {path} ({'; '.join(errors)})")


def _period_from_name(path: Path) -> str:
    match = PERIOD_PATTERN.search(path.stem)
    if not match:
        raise ValidationError(
            f"파일명에서 YYYYMM 분기를 찾을 수 없습니다: {path.name}"
        )
    return match.group(1)


def _normalized_codes(values: pd.Series) -> frozenset[str]:
    return frozenset(
        values.dropna().astype(str).str.strip().str.replace(r"\.0$", "", regex=True)
    )


def validate_csv(path: Path) -> CsvSummary:
    period = _period_from_name(path)
    frame = _read_csv(path)
    missing = sorted(REQUIRED_COLUMNS - set(frame.columns))
    if missing:
        raise ValidationError(f"{path.name} 필수 컬럼 누락: {', '.join(missing)}")

    if frame[list(REQUIRED_COLUMNS)].isna().any().any():
        null_columns = sorted(
            column for column in REQUIRED_COLUMNS if frame[column].isna().any()
        )
        raise ValidationError(
            f"{path.name} 필수 컬럼에 빈 값 존재: {', '.join(null_columns)}"
        )

    dong_codes = _normalized_codes(frame["행정동코드"])
    summary = CsvSummary(
        path=path,
        period=period,
        row_count=len(frame),
        dong_codes=dong_codes,
        major_category_count=frame["상권업종대분류코드"].nunique(),
        middle_category_count=frame["상권업종중분류코드"].nunique(),
    )

    expected_rows = EXPECTED_COUNTS.get(period)
    if expected_rows is not None and summary.row_count != expected_rows:
        raise ValidationError(
            f"{period} 점포 수 불일치: expected={expected_rows}, actual={summary.row_count}"
        )
    if len(dong_codes) != EXPECTED_DONG_COUNT:
        raise ValidationError(
            f"{period} 행정동 수 불일치: expected={EXPECTED_DONG_COUNT}, "
            f"actual={len(dong_codes)}"
        )
    if summary.major_category_count != EXPECTED_MAJOR_CATEGORY_COUNT:
        raise ValidationError(
            f"{period} 대분류 수 불일치: expected={EXPECTED_MAJOR_CATEGORY_COUNT}, "
            f"actual={summary.major_category_count}"
        )
    if summary.middle_category_count != EXPECTED_MIDDLE_CATEGORY_COUNT:
        raise ValidationError(
            f"{period} 중분류 수 불일치: expected={EXPECTED_MIDDLE_CATEGORY_COUNT}, "
            f"actual={summary.middle_category_count}"
        )
    return summary


def _quarter_index(period: str) -> int:
    year = int(period[:4])
    month = int(period[4:])
    return year * 4 + (month // 3 - 1)


def validate_periods(summaries: list[CsvSummary]) -> None:
    periods = [summary.period for summary in summaries]
    if len(periods) != len(set(periods)):
        raise ValidationError("동일한 분기의 CSV 파일이 두 개 이상 존재합니다.")
    ordered = sorted(periods, key=_quarter_index)
    for previous, current in zip(ordered, ordered[1:]):
        if _quarter_index(current) - _quarter_index(previous) != 1:
            raise ValidationError(f"분기가 연속되지 않습니다: {previous} -> {current}")


def validate_geojson(path: Path, expected_codes: frozenset[str]) -> str:
    with path.open(encoding="utf-8-sig") as stream:
        data = json.load(stream)
    if data.get("type") != "FeatureCollection" or not isinstance(
        data.get("features"), list
    ):
        raise ValidationError(f"GeoJSON이 FeatureCollection이 아닙니다: {path}")

    features = data["features"]
    if not features:
        raise ValidationError(f"GeoJSON feature가 없습니다: {path}")
    daejeon_features = [
        feature
        for feature in features
        if feature.get("properties", {}).get("sidonm") == "대전광역시"
        or str(feature.get("properties", {}).get("adm_nm", "")).startswith(
            "대전광역시 "
        )
    ]
    if not daejeon_features:
        raise ValidationError(f"GeoJSON에 대전광역시 행정동이 없습니다: {path}")
    invalid_geometry = [
        feature.get("properties", {}).get("adm_nm", "알 수 없음")
        for feature in daejeon_features
        if feature.get("geometry", {}).get("type") not in {"Polygon", "MultiPolygon"}
    ]
    if invalid_geometry:
        raise ValidationError(
            f"대전 행정동 경계 geometry가 Polygon이 아닙니다: {invalid_geometry}"
        )
    properties = daejeon_features[0].get("properties") or {}
    available_properties = [
        name for name in GEO_CODE_CANDIDATES if name in properties
    ]
    if not available_properties:
        raise ValidationError(
            "GeoJSON 행정동 코드 속성을 찾을 수 없습니다. "
            f"확인 대상: {', '.join(GEO_CODE_CANDIDATES)}"
        )

    code_property = ""
    geo_codes: frozenset[str] = frozenset()
    for candidate in available_properties:
        candidate_codes = frozenset(
            str(feature.get("properties", {}).get(candidate, "")).strip()[:8]
            if candidate == "adm_cd2"
            else str(feature.get("properties", {}).get(candidate, "")).strip()
            for feature in daejeon_features
            if feature.get("properties", {}).get(candidate) is not None
        )
        if candidate_codes == expected_codes:
            code_property = candidate
            geo_codes = candidate_codes
            break
    if not code_property:
        code_property = available_properties[0]
        geo_codes = frozenset(
            str(feature.get("properties", {}).get(code_property, "")).strip()[:8]
            if code_property == "adm_cd2"
            else str(feature.get("properties", {}).get(code_property, "")).strip()
            for feature in daejeon_features
            if feature.get("properties", {}).get(code_property) is not None
        )
    missing_geo = sorted(expected_codes - geo_codes)
    unknown_geo = sorted(geo_codes - expected_codes)
    if missing_geo or unknown_geo:
        raise ValidationError(
            "GeoJSON 행정동 코드 불일치: "
            f"missing={missing_geo or '없음'}, unknown={unknown_geo or '없음'}"
        )
    return code_property


def validate_inputs(raw_dir: Path, geojson_path: Path) -> list[CsvSummary]:
    csv_paths = sorted(raw_dir.glob("*.csv"))
    if not csv_paths:
        raise ValidationError(f"원본 CSV가 없습니다: {raw_dir}")
    if not geojson_path.is_file():
        raise ValidationError(f"GeoJSON 파일이 없습니다: {geojson_path}")

    summaries = [validate_csv(path) for path in csv_paths]
    validate_periods(summaries)
    reference_codes = summaries[-1].dong_codes
    for summary in summaries[:-1]:
        if summary.dong_codes != reference_codes:
            raise ValidationError(
                f"분기별 행정동 코드 구성이 다릅니다: {summary.period}"
            )
    validate_geojson(geojson_path, reference_codes)
    return summaries


def main() -> int:
    project_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--raw-dir", type=Path, default=project_root / "data" / "raw")
    parser.add_argument(
        "--geojson",
        type=Path,
        default=project_root
        / "data"
        / "geo"
        / "HangJeongDong_ver20260701.geojson",
    )
    args = parser.parse_args()

    try:
        summaries = validate_inputs(args.raw_dir, args.geojson)
    except (ValidationError, OSError, json.JSONDecodeError) as exc:
        print(f"검증 실패: {exc}", file=sys.stderr)
        return 1

    for summary in sorted(summaries, key=lambda item: _quarter_index(item.period)):
        print(
            f"{summary.period}: 점포 {summary.row_count:,}개, "
            f"행정동 {len(summary.dong_codes)}개, "
            f"대분류 {summary.major_category_count}개, "
            f"중분류 {summary.middle_category_count}개"
        )
    print("입력 데이터 검증 완료")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
