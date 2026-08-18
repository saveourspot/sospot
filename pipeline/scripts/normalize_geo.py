"""Create the normalized Daejeon administrative-dong GeoJSON."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Iterator


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_INPUT = REPOSITORY_ROOT / "data" / "geo" / "HangJeongDong_ver20260701.geojson"
DEFAULT_OUTPUT = REPOSITORY_ROOT / "data" / "geo" / "daejeon_dong.geojson"

DAEJEON_SIDO_CODE = "30"
EXPECTED_DONG_COUNT = 82
MIN_LONGITUDE = 127.24
MAX_LONGITUDE = 127.56
MIN_LATITUDE = 36.18
MAX_LATITUDE = 36.51


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Filter the nationwide GeoJSON to Daejeon's 82 administrative dongs."
    )
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    return parser.parse_args()


def iter_coordinates(value: Any) -> Iterator[tuple[float, float]]:
    if (
        isinstance(value, list)
        and len(value) >= 2
        and isinstance(value[0], (int, float))
        and isinstance(value[1], (int, float))
    ):
        yield float(value[0]), float(value[1])
        return

    if isinstance(value, list):
        for child in value:
            yield from iter_coordinates(child)


def normalize_feature(feature: dict[str, Any]) -> dict[str, Any]:
    properties = dict(feature.get("properties") or {})
    adm_cd2 = str(properties.get("adm_cd2", ""))
    adm_nm = str(properties.get("adm_nm", "")).strip()

    if len(adm_cd2) < 8 or not adm_cd2.isdigit():
        raise ValueError(f"Invalid adm_cd2: {adm_cd2!r}")
    if not adm_nm:
        raise ValueError(f"Missing adm_nm for adm_cd2={adm_cd2}")

    properties["dong_code"] = adm_cd2[:8]
    properties["dong_name"] = adm_nm.split()[-1]

    normalized = dict(feature)
    normalized["properties"] = properties
    return normalized


def validate_features(features: list[dict[str, Any]]) -> None:
    if len(features) != EXPECTED_DONG_COUNT:
        raise ValueError(
            f"Expected {EXPECTED_DONG_COUNT} Daejeon features, found {len(features)}"
        )

    dong_codes = [feature["properties"]["dong_code"] for feature in features]
    if any(len(code) != 8 or not code.isdigit() for code in dong_codes):
        raise ValueError("Every dong_code must be an 8-digit string")
    if len(set(dong_codes)) != EXPECTED_DONG_COUNT:
        raise ValueError("dong_code values must be unique")

    coordinates = [
        coordinate
        for feature in features
        for coordinate in iter_coordinates((feature.get("geometry") or {}).get("coordinates"))
    ]
    if not coordinates:
        raise ValueError("No geometry coordinates found")

    longitudes, latitudes = zip(*coordinates)
    bounds = (min(longitudes), min(latitudes), max(longitudes), max(latitudes))
    if not (
        MIN_LONGITUDE <= bounds[0] <= bounds[2] <= MAX_LONGITUDE
        and MIN_LATITUDE <= bounds[1] <= bounds[3] <= MAX_LATITUDE
    ):
        raise ValueError(
            "Daejeon coordinate bounds are outside the expected range: "
            f"lon={bounds[0]:.4f}..{bounds[2]:.4f}, "
            f"lat={bounds[1]:.4f}..{bounds[3]:.4f}"
        )

    print(
        f"Validated {len(features)} features and {len(set(dong_codes))} unique dong codes; "
        f"bounds=({bounds[0]:.4f}, {bounds[1]:.4f})..({bounds[2]:.4f}, {bounds[3]:.4f})"
    )


def normalize_geojson(input_path: Path, output_path: Path) -> None:
    if not input_path.is_file():
        raise FileNotFoundError(
            f"Source GeoJSON not found: {input_path}\n"
            "Download it using the instructions in data/geo/README.md."
        )

    with input_path.open(encoding="utf-8") as source:
        geojson = json.load(source)

    if geojson.get("type") != "FeatureCollection" or not isinstance(
        geojson.get("features"), list
    ):
        raise ValueError("Source must be a GeoJSON FeatureCollection")

    features = [
        normalize_feature(feature)
        for feature in geojson["features"]
        if str((feature.get("properties") or {}).get("sido", "")) == DAEJEON_SIDO_CODE
    ]
    validate_features(features)

    normalized = {key: value for key, value in geojson.items() if key != "features"}
    normalized["features"] = features

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8", newline="\n") as destination:
        json.dump(normalized, destination, ensure_ascii=False, separators=(",", ":"))
        destination.write("\n")

    print(f"Wrote normalized GeoJSON: {output_path}")


def main() -> None:
    args = parse_args()
    normalize_geojson(args.input.resolve(), args.output.resolve())


if __name__ == "__main__":
    main()
