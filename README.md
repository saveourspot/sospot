# SOSpot

> **Save Our Spot** — 소상공인 상권 이상징후 탐지 서비스
> 2026년도 공공데이터 활용 공모전 (대전지역 공공데이터 활성화 협의체 · 대전권대학 산학협의체)

---

## 무엇을 하는 서비스인가

대전 상권은 최근 분기에 점포 수가 **늘었습니다**(78,246 → 80,704). 그런데 소상공인 체감경기(BSI)는 계속 나빠졌습니다(72.7 → 60.0). 총량만 보면 상권이 좋아 보이지만 현장 체감은 반대입니다.

그래서 SOSpot은 **"어디가 줄었나"가 아니라 "대전 전체가 오르는데 어디만 안 올랐나"** 를 찾습니다.

```
상대격차(RD) = 지역 증감률 − 대전 전체 동일 업종 증감률
```

### 실제 탐지 사례

| 지역 | 업종 | 점포수 추이 | 지역 | 대전 전체 | 상대격차 |
|---|---|---|---|---|---|
| 중구 목동 | 음식 | 123 → 121 → 117 | −3.3% | **+3.0%** | **−6.3%p** |

117개는 여전히 많고 감소폭도 작아 단순 조회로는 눈에 띄지 않습니다. 하지만 대전 전체 음식업이 성장하는 동안 혼자 줄었습니다. 이게 상대격차 방식이 아니면 못 잡는 신호입니다.

### 핵심 기능

- **이상징후 지도** — 대전 82개 행정동을 4단계 등급(정상 / 관심 / 주의 / 중점검토)으로 표시
- **근거 기반 상세 분석** — 모든 판정에 점포수 추이·증감률·상대격차를 함께 제시
- **성장 모멘텀과 정책 검토 카드** — 최근 흐름이 좋은 업종과 현장에서 추가 확인할 검토 항목 제시
- **유사 상권 벤치마킹** — 업종 구성이 비슷한 행정동끼리 비교해 상대격차 우위 업종과 접목 전 확인사항 제시
- **AI 자연어 질의응답** — "최근 음식업이 계속 줄어든 지역은?" 같은 질문에 **실제 분석 API 결과로만** 응답

AI와 대시보드가 같은 분석 API를 호출하므로 두 화면의 숫자가 어긋나지 않습니다.

> SOSpot의 등급은 폐업 가능성이나 절대 위험도가 아니라 대전 내 상대적인 검토 우선순위입니다. 점포 수 감소가 개별 점포의 폐업을 의미하지 않으며, 정책 검토 카드는 정책 효과를 예측하거나 지원 대상을 자동 결정하지 않습니다.

### 등급 기준과 해석

경계값은 해당 등급에 포함합니다. 업종별 이상징후 Score는 `중점검토 80 이상`, `주의 65 이상 80 미만`, `관심 50 이상 65 미만`, `정상 50 미만`입니다. 지도에 표시하는 행정동 종합 등급은 82개 동의 상대 순위(percentile)를 기준으로 `중점검토 90 이상`, `주의 70 이상 90 미만`, `관심 40 이상 70 미만`, `정상 40 미만`입니다.

두 등급 모두 위험 확률이 아니라 정책 담당자가 자료를 더 살펴볼 순서를 추천하는 범위입니다. 기준 분기보다 두 분기 전 점포 수가 20개 미만인 업종 조합은 정상으로 처리하지 않고 `표본 부족으로 판정 제외`합니다.

### 기획·심사 핵심 Q&A

**Q. 단순히 점포 수가 줄어든 지역을 찾는 서비스인가요?**

A. 아닙니다. 해당 지역의 변화율에서 대전 전체 동일 업종 변화율을 뺀 상대격차를 사용해, 도시 전체 흐름과 다르게 움직이는 지역을 찾습니다.

**Q. 등급이 높으면 폐업 위험이 높다는 뜻인가요?**

A. 아닙니다. 대전 내 상대적인 검토 우선순위이며 개별 점포의 폐업이나 미래 매출을 예측하지 않습니다.

**Q. 표본이 작은 업종도 평가하나요?**

A. 기준 분기보다 두 분기 전 점포 수가 20개 미만이면 점수와 등급을 계산하지 않고 판정에서 제외합니다.

**Q. BSI는 이상징후 점수에 들어가나요?**

A. 들어가지 않습니다. BSI는 대전 전체 경기 흐름을 설명하는 보조 맥락으로만 사용합니다.

**Q. ‘비슷한 상권 중 상대적으로 양호한 지역’은 어떻게 찾나요?**

A. 최신 분기의 대분류 업종별 점포 구성으로 상권 유사도를 비교한 뒤, 유사도가 높은 후보 중 선택 지역보다 상대격차가 양호한 업종이 많은 2개 지역을 보여줍니다. 인과관계나 정책 성공 사례를 뜻하지 않으므로 입지·고객층·주변 업종을 현장에서 먼저 비교합니다.

**Q. AI가 점수나 정책을 직접 결정하나요?**

A. 아닙니다. Backend와 Pipeline이 계산한 동일한 API 결과만 설명하며, 정책 검토 카드는 현장 확인 항목을 제시할 뿐 지원 대상을 자동 결정하지 않습니다.

**Q. 새 분기 CSV를 넣으면 무엇이 갱신되나요?**

A. 파이프라인을 실행하면 점포 집계·이상징후·행정동 순위가 새 snapshot으로 저장되고 지도, 상세 그래프, 벤치마킹, AI 조회 결과가 최신 분석 완료 분기로 갱신됩니다.

---

## 기술 스택

| 레이어 | 기술 |
|---|---|
| Backend | Spring Boot 4.1 · Java 17 · Gradle · Spring Data JPA |
| DB | PostgreSQL 16 (Docker) · Flyway |
| 분석 파이프라인 | Python 3.11 · pandas |
| Frontend | React · Vite · Leaflet · Recharts |
| AI | Google Gemini Tool Calling (Spring `RestClient`) |

---

## 활용 공공데이터

소상공인시장진흥공단 제공 (공공데이터포털)

| 데이터셋 | 용도 |
|---|---|
| 상가(상권)정보_대전 | 핵심 분석 소스 — 행정동×업종 점포수 집계 (2025.12 / 2026.03 / 2026.06) |
| 소상공인 경기동향(BSI) 현황 | 대전 전체 경기 흐름을 설명하는 보조 맥락 (이상징후 Score 계산에는 미사용) |

---

## 프로젝트 구조

```
sospot/
├── backend/          # Spring Boot API + Flyway 마이그레이션
├── frontend/         # React + Vite + Leaflet
├── pipeline/         # Python 전처리·집계·지표 계산
├── data/
│   ├── raw/          # 원본 CSV (커밋 제외)
│   └── geo/          # 행정동 경계 GeoJSON
└── docker-compose.yml
```

---

## 시작하기

### 사전 준비

- Docker Desktop (실행 중이어야 함)
- JDK 17
- Node.js 20.19+ 또는 22.12+
- Python 3.11

### 1. 클론 및 IDE 열기

```bash
git clone <repo-url>
cd sospot
```

IntelliJ에서 **루트 폴더**를 열고, `backend/build.gradle` 우클릭 → **Link Gradle Project**

> `backend/`를 직접 열지 마세요. 루트를 열어야 frontend·pipeline이 같은 창에 들어옵니다.

### 2. 설정 파일 2개 만들기

**`.env`** (루트) — 다음 내용을 작성

```
DB_USER=sospot
DB_PASSWORD=팀_공유_비밀번호
```

**`backend/src/main/resources/application-local.yml`** — `application-local.yml.example` 복사

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sospot
    username: sospot
    password: 팀_공유_비밀번호
    driver-class-name: org.postgresql.Driver

llm:
  google:
    api-key: Google_AI_Studio_API_키
```

> **두 파일의 DB 비밀번호가 같아야 합니다.** 다르면 백엔드가 DB 인증에 실패합니다. Gemini API 키가 비어 있거나 외부 호출에 실패하면 AI 화면은 실제 분석 API 결과를 이용한 기본 안내를 제공합니다.
> 두 파일 모두 `.gitignore`에 포함되어 있습니다. **절대 커밋하지 마세요.**

### 3. IntelliJ 설정 4가지

| 경로 | 값 |
|---|---|
| `Build Tools > Gradle` | Build and run using / Run tests using → **Gradle** |
| `Compiler > Annotation Processors` | **Enable annotation processing** 체크 |
| `Editor > File Encodings` | 전부 **UTF-8**, `Transparent native-to-ascii` 해제 |
| `Tools > Actions on Save` | Reformat code, Optimize imports |

인코딩이 중요합니다. **BSI 원본 CSV가 cp949**라서 설정이 흔들리면 콘솔 한글이 깨집니다.

### 4. DB 기동

```powershell
docker compose up -d db
docker compose ps          # STATUS가 healthy면 정상
```

### 5. 백엔드 실행

IntelliJ에서 `SospotApplication` 실행, 또는

```powershell
cd backend
.\gradlew.bat bootRun
```

Flyway가 마이그레이션을 자동 적용합니다. 아래가 뜨면 성공입니다.

```
Successfully applied 3 migrations
Tomcat started on port 8080 (http)
Started SospotApplication
```

### 6. 프론트엔드

```powershell
cd frontend
npm install
npm run dev              # http://localhost:5173
```

### 7. Python 파이프라인

프로젝트 루트에서 가상환경을 만들고 의존성을 설치합니다. 최초 한 번만 실행하면 됩니다.

```powershell
python -m venv pipeline/.venv
pipeline\.venv\Scripts\python.exe -X utf8 -m pip install -r pipeline/requirements.txt
```

IntelliJ에서 사용할 경우 `File > Project Structure > Modules > + > Python`으로 `pipeline` 모듈을 추가하고, 인터프리터를 `pipeline/.venv`로 지정합니다. IDE의 import 표시를 위해 `pipeline/src`를 Sources Root로 설정할 수 있지만, 아래 명령 실행에는 필수가 아닙니다.

초기 데이터 적재 또는 새 분기 추가 방법은 [신규 분기 CSV 반영](#신규-분기-csv-반영)을 따릅니다.

---

## 신규 분기 CSV 반영

CSV를 `data/raw/`에 복사하는 것만으로 화면이 자동 갱신되지는 않습니다. 파일을 추가한 뒤 아래 파이프라인 4개를 순서대로 실행해야 합니다.

### 1. CSV 준비

신규 파일을 다음 형식의 이름으로 저장합니다.

```text
data/raw/소상공인시장진흥공단_상가(상권)정보_대전_YYYYMM.csv
```

예를 들어 2026년 3분기 자료는 다음과 같습니다.

```text
data/raw/소상공인시장진흥공단_상가(상권)정보_대전_202609.csv
```

입력 파일은 다음 조건을 모두 만족해야 합니다.

- UTF-8 CSV
- 분기 값이 `03`, `06`, `09`, `12` 중 하나로 끝남
- `data/raw/`에 있는 이전 파일과 연속된 분기
- 같은 분기의 CSV가 중복되지 않음
- 다음 9개 컬럼에 누락값이 없음

```text
행정동코드
행정동명
시군구명
상권업종대분류코드
상권업종대분류명
상권업종중분류코드
상권업종중분류명
경도
위도
```

원본 CSV의 내용이나 컬럼명을 직접 수정하지 않는 것을 원칙으로 합니다.

### 2. PostgreSQL 실행 및 상태 확인

프로젝트 루트에서 실행합니다.

```powershell
docker compose up -d db
docker compose ps
```

`sospot-db`의 상태가 `healthy`가 된 후 다음 단계로 진행합니다. 테이블이 아직 없다면 백엔드를 한 번 실행해 Flyway 마이그레이션을 먼저 적용합니다.

```powershell
cd backend
.\gradlew.bat bootRun
```

Flyway 적용이 끝나고 백엔드가 실행되면 `Ctrl+C`로 종료한 뒤 프로젝트 루트로 돌아옵니다.

```powershell
cd ..
```

### 3. CSV 검증

```powershell
pipeline\.venv\Scripts\python.exe -X utf8 -m pipeline.src.load
```

기존 검증 데이터와 신규 분기의 행 수가 출력됩니다. 신규 분기는 고정된 예상 건수와 비교하지 않고 `(structure validated)`가 표시되면 구조 검증에 성공한 것입니다.

### 4. 기준정보와 점포 수 집계 적재

다음 두 명령을 순서대로 실행합니다.

```powershell
pipeline\.venv\Scripts\python.exe -X utf8 -m pipeline.src.dimensions
pipeline\.venv\Scripts\python.exe -X utf8 -m pipeline.src.aggregate
```

- `dimensions`: CSV에서 분기·업종 기준정보를 만들고, GeoJSON과 행정동 코드 82개가 일치하는지 검사한 뒤 DB에 반영합니다.
- `aggregate`: 행정동 × 업종 × 분기 점포 수를 집계하여 `fact_store_count`에 신규 분기만 추가합니다.

정상적으로 추가되면 `aggregate` 출력에서 신규 분기 옆에 `inserted`가 표시됩니다. `already stored`라면 그 분기는 이미 DB에 적재된 상태입니다.

### 5. 이상징후 분석

```powershell
pipeline\.venv\Scripts\python.exe -X utf8 -m pipeline.src.metrics
```

연속된 최근 3개 분기를 이용해 업종별 이상징후와 행정동 종합 점수를 계산하고 다음 테이블에 분기별 snapshot으로 저장합니다.

```text
fact_anomaly
fact_dong_score
```

예를 들어 `202609`를 추가하면 신규 분석 구간은 다음과 같습니다.

```text
202603 → 202606 → 202609
```

기존 `202606` 분석 결과는 보존됩니다. Backend는 `fact_dong_score`에 분석 결과가 존재하는 가장 최신 분기를 기본값으로 사용하므로 특정 분기를 코드에서 바꿀 필요가 없습니다.

### 6. 웹 화면 확인

백엔드와 프론트엔드를 실행한 뒤 브라우저를 새로고침합니다.

```powershell
# 터미널 1
cd backend
.\gradlew.bat bootRun

# 터미널 2
cd frontend
npm run dev
```

파이프라인이 정상 완료되면 다음 항목이 최신 분석 완료 분기를 기준으로 갱신됩니다.

- 메인 요약과 기준 시점
- 이상징후 지도와 행정동 순위
- 행정동 상세 그래프와 상대격차
- 성장 모멘텀 업종과 정책 검토 카드
- 유사 상권의 상대격차 우위 업종과 벤치마킹 검토 방향
- AI가 Tool/API로 조회하는 분석 결과

정책 검토 문구 자체는 CSV에서 학습해 새로 생성하는 것이 아닙니다. Backend가 새 수치로 성장 흐름 유형과 대상 업종을 다시 고르고, 해당 업종에 미리 정의된 현장 확인 체크리스트를 연결합니다.

### 주의: 이미 적재된 분기 CSV를 바꾼 경우

`aggregate`는 과거 snapshot 보호를 위해 이미 저장된 분기를 자동으로 덮어쓰지 않습니다. 따라서 기존 분기 CSV의 내용을 바꾼 뒤 같은 명령을 실행해도 `already stored`로 표시됩니다.

개발 DB의 기존 데이터를 삭제하거나 다시 적재해야 한다면 데이터 보존에 영향을 주므로, 팀원과 범위를 확인한 뒤 진행해야 합니다. 운영 또는 공유 DB에서 임의로 삭제하지 마세요.

---

## 개발 명령

Windows PowerShell 기준입니다. 백엔드와 프론트엔드는 각각 별도 터미널에서 실행합니다.

```powershell
docker compose up -d db    # DB 기동 (PC 재부팅 후 필요)
docker compose down        # DB 정지, 데이터 볼륨 유지

cd backend
.\gradlew.bat bootRun      # 백엔드 :8080

cd frontend
npm run dev                # 프론트 :5173
```

프론트의 `/api` 요청은 Vite 프록시가 8080으로 넘깁니다. 별도 CORS 설정이 필요 없습니다.

`-X utf8`은 Windows 콘솔에서 한글과 검증 완료 기호가 깨지는 문제를 방지합니다. macOS/Linux에서는 `pipeline\.venv\Scripts\python.exe` 대신 `pipeline/.venv/bin/python`, `.\gradlew.bat` 대신 `./gradlew`를 사용합니다.

---

## 자주 발생하는 오류

### `package org.springframework.boot does not exist`

의존성은 받아졌는데 컴파일만 실패하는 경우. IntelliJ 모듈 설정이 깨진 상태입니다.

1. `Build and run using`을 **Gradle**로 변경
2. `File > Invalidate Caches... > Invalidate and Restart`
3. 그래도 안 되면 터미널에서 `cd backend` 후 `.\gradlew.bat bootRun` 실행

### `Directory ... does not contain a Gradle build`

`.idea/gradle.xml`에 옛 경로 링크가 남은 경우.

Gradle 창에서 루트를 가리키는 항목 우클릭 → `Unlink Gradle Project`
안 보이면 `.idea/gradle.xml`에서 `externalProjectPath`가 `$PROJECT_DIR$`인 블록 삭제.

### Docker `client version 1.24 is too old`

IntelliJ가 Docker 데몬 소켓을 못 잡은 경우.

Docker Desktop을 먼저 켜고 IntelliJ를 재시작하면 대개 잡힙니다.
**안 되면 터미널로 우회하세요.** IDE 연동은 편의 기능이고 결과는 동일합니다.

### `port is already allocated`

로컬에 PostgreSQL이 설치돼 5432가 점유된 경우.
`docker-compose.yml`의 포트를 `"5433:5432"`로 바꾸고 `application-local.yml`의 URL도 `localhost:5433`으로 맞추세요.

### DB 비밀번호를 바꿨는데 반영되지 않음

`POSTGRES_PASSWORD`는 볼륨이 처음 만들어질 때만 적용됩니다.

```bash
docker compose down -v
docker compose up -d
```

### Flyway `syntax error at or near "#"`

마이그레이션 SQL 파일에 마크다운 잔재가 들어간 경우. **파일 첫 줄이 `CREATE TABLE`로 시작**해야 합니다. SQL 주석은 `#`이 아니라 `--`입니다.

실패한 마이그레이션은 자동 롤백되므로 DB를 지울 필요 없이 고치고 재실행하면 됩니다.

### Flyway `Validate failed: Migration checksum mismatch`

**이미 적용된 마이그레이션 파일을 수정**한 경우입니다.

적용된 SQL은 절대 수정하지 마세요. 변경이 필요하면 `V4__...sql`을 새로 만듭니다.
로컬에서 정말 초기화해야 한다면 팀원과 데이터 삭제 범위를 먼저 확인한 뒤 PostgreSQL 볼륨을 재생성합니다. `docker compose down -v`는 적재된 분석 데이터를 모두 삭제합니다.

### 지도가 회색 조각으로 깨짐

`frontend/src/main.jsx`에 Leaflet CSS import가 빠진 경우.

```js
import 'leaflet/dist/leaflet.css'
```

### 마이그레이션이 적용되지 않고 조용히 넘어감

`resources/db/migration/` 경로가 아니거나(`db.migration` 단일 폴더), 파일명 언더스코어가 1개인 경우입니다. `V1__init_dimensions.sql`처럼 **언더스코어 2개**여야 합니다.

---

## 마이그레이션 규칙

- 경로: `backend/src/main/resources/db/migration/`
- 파일명: `V{번호}__{설명}.sql` (**언더스코어 2개**)
- **적용된 마이그레이션은 절대 수정 금지** — 변경은 새 버전 파일로
- SQL 파일에는 순수 SQL만, 주석은 `--`

---

## 커밋 전 확인

- [ ] `.env`가 스테이징에 없는가
- [ ] `application-local.yml`이 스테이징에 없는가
- [ ] `data/raw/`의 CSV가 포함되지 않았는가 (파일당 40MB+)
- [ ] `out/`, `build/`, `node_modules/`가 제외됐는가

---

## 데이터 출처

소상공인시장진흥공단, 공공데이터포털 ([data.go.kr](https://www.data.go.kr))
행정동 경계 데이터: 행정안전부
