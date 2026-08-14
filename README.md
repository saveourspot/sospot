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
- **AI 자연어 질의응답** — "최근 음식업이 계속 줄어든 지역은?" 같은 질문에 **실제 분석 API 결과로만** 응답

AI와 대시보드가 같은 분석 API를 호출하므로 두 화면의 숫자가 어긋나지 않습니다.

---

## 기술 스택

| 레이어 | 기술 |
|---|---|
| Backend | Spring Boot 4.1 · Java 17 · Gradle · Spring Data JPA |
| DB | PostgreSQL 16 (Docker) · Flyway |
| 분석 파이프라인 | Python 3.11 · pandas |
| Frontend | React · Vite · Leaflet · Recharts |
| AI | Tool Calling (WebClient REST) |

---

## 활용 공공데이터

소상공인시장진흥공단 제공 (공공데이터포털)

| 데이터셋 | 용도 |
|---|---|
| 상가(상권)정보_대전 | 핵심 분석 소스 — 행정동×업종 점포수 집계 (2025.12 / 2026.03 / 2026.06) |
| 소상공인 경기동향(BSI) 현황 | 경기 맥락 지표 — 방법론의 근거 |

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
- Node.js 20+
- Python 3.11

### 1. 클론 및 IDE 열기

```bash
git clone <repo-url>
cd sospot
```

IntelliJ에서 **루트 폴더**를 열고, `backend/build.gradle` 우클릭 → **Link Gradle Project**

> `backend/`를 직접 열지 마세요. 루트를 열어야 frontend·pipeline이 같은 창에 들어옵니다.

### 2. 설정 파일 2개 만들기

**`.env`** (루트) — `.env.example` 복사

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
  api-key:
  base-url: https://api.anthropic.com
```

> **두 파일의 비밀번호가 같아야 합니다.** 다르면 백엔드가 인증 실패로 죽습니다.
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

```bash
docker compose up -d
docker compose ps          # STATUS가 healthy면 정상
```

### 5. 백엔드 실행

IntelliJ에서 `SospotApplication` 실행, 또는

```bash
cd backend && ./gradlew bootRun
```

Flyway가 마이그레이션을 자동 적용합니다. 아래가 뜨면 성공입니다.

```
Successfully applied 3 migrations
Tomcat started on port 8080 (http)
Started SospotApplication
```

### 6. 프론트엔드

```bash
cd frontend
npm install
npm run dev              # http://localhost:5173
```

### 7. Python 파이프라인

`File > Project Structure > Modules > + > Python`으로 `pipeline`을 모듈로 추가하고 venv 생성 후:

```bash
pip install -r pipeline/requirements.txt
```

`Sources` 탭에서 `pipeline/src`를 **Sources Root**로 지정해야 임포트가 해석됩니다.

---

## 개발 명령

```bash
docker compose up -d      # DB 기동 (PC 재부팅 후 매번 필요)
docker compose down       # 정지 (데이터 유지)
docker compose down -v    # 정지 + 볼륨 삭제

cd backend && ./gradlew bootRun    # 백엔드 :8080
cd frontend && npm run dev         # 프론트 :5173
```

프론트의 `/api` 요청은 Vite 프록시가 8080으로 넘깁니다. 별도 CORS 설정이 필요 없습니다.

---

## 자주 발생하는 오류

### `package org.springframework.boot does not exist`

의존성은 받아졌는데 컴파일만 실패하는 경우. IntelliJ 모듈 설정이 깨진 상태입니다.

1. `Build and run using`을 **Gradle**로 변경
2. `File > Invalidate Caches... > Invalidate and Restart`
3. 그래도 안 되면 터미널: `cd backend && ./gradlew bootRun`

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
로컬에서 초기화하려면 `docker compose down -v && docker compose up -d`.

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
