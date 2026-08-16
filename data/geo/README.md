# GeoJSON 데이터

## 파일 구성

| 파일 | git 커밋 | 생성 방법 |
|---|---|---|
| `HangJeongDong_ver20260701.geojson` | ❌ 제외 (34MB) | 아래 원본 다운로드 |
| `daejeon_dong.geojson` | ✅ 커밋 | P1-05 스크립트 산출물 |

## 원본 다운로드

전국 행정동 경계 GeoJSON:
- 배포 출처: [`vuski/admdongkor`](https://github.com/vuski/admdongkor/tree/master/ver20260701)
- 경계 기반: 통계청 SGIS 행정동 경계
- 코드 체계: `adm_cd2`는 행정안전부 10자리 행정기관코드
- 원본 파일: [`HangJeongDong_ver20260701.geojson`](https://github.com/vuski/admdongkor/blob/master/ver20260701/HangJeongDong_ver20260701.geojson)
- 파일명: `HangJeongDong_ver20260701.geojson`
- 크기: 약 34MB
- 좌표계: CRS84 (WGS84)
- feature 수: 전국 3,558개

## 정규화 절차

원본을 받은 뒤 다음 스크립트 실행 (P1-05 완료 후):

```bash
python pipeline/scripts/normalize_geo.py
```

산출물: `daejeon_dong.geojson`
- 대전 82개 feature만 필터 (`sido == '30'`)
- 각 feature.properties에 추가:
  - `dong_code`: 8자리 (`adm_cd2[:8]`)
  - `dong_name`: 행정동명 (`adm_nm`의 마지막 토큰)

## 검증 결과 (기 확정)

- 대전 feature: **82개**
- 자치구 분포: 동16 · 중17 · 서24 · 유13 · 대12
- 좌표 범위: 위도 36.1833~36.5004, 경도 127.2468~127.5573
- **D1 상가정보 행정동코드와 82/82 완전 매칭** (`adm_cd2[:8] == D1.행정동코드`)
