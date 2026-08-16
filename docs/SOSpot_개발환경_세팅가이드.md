# SOSpot 개발환경 세팅 가이드 (실행 순서판)

> 실제 구축하며 확인한 순서 그대로입니다. **위에서부터 차례대로** 따라가면 됩니다.
> 최종 확인 환경: IntelliJ IDEA Ultimate 2024.3.1.1 · DataGrip · Zulu JDK 17 · Spring Boot 4.1.0 · PostgreSQL 16 · Vite 8.2.1
> 막히면 **부록 A 트러블슈팅**을 보세요. 실제로 걸렸던 7가지가 정리돼 있습니다.

---

## 0. 사전 준비

- [ ] **Docker Desktop 설치 및 실행** — 좌하단이 `Engine running` 초록색인지 확인
- [ ] IntelliJ IDEA **Ultimate**, DataGrip 설치
- [ ] Python 3.11 설치 확인

> **툴은 2개만 씁니다.** IntelliJ Ultimate 하나로 백엔드·프론트·Python을 다 하고, DataGrip만 따로 씁니다. WebStorm·PyCharm은 설치하지 마세요. 한 저장소를 IDE 3개로 열면 인덱싱이 3번 돌고 Git 상태가 창마다 어긋납니다.

---

## 1. 저장소 구조 만들기

프로젝트 루트에 아래 구조를 만듭니다. `backend/`는 다음 단계에서 채워집니다.

```
sospot/
├── docker-compose.yml
├── .env
├── .env.example
├── .gitignore
├── backend/
├── frontend/
├── pipeline/
└── data/
    ├── raw/      # 원본 CSV (커밋 안 함)
    └── geo/      # 행정동 GeoJSON
```

**`.gitignore`**

```
.env
data/raw/
backend/build/
backend/.gradle/
backend/src/main/resources/application-local.yml
frontend/node_modules/
frontend/dist/
pipeline/.venv/
pipeline/__pycache__/
.idea/workspace.xml
.idea/httpRequests/
```

`.idea/` 전체를 무시하지는 마세요. 다만 **실행 구성을 프로젝트 파일로 저장하지 마세요** — 환경변수에 키를 넣었을 경우 그대로 커밋됩니다.

---

## 2. Spring Boot 프로젝트 생성

`File > New > Project > Spring Boot`

| 항목 | 값 |
|---|---|
| **Location** | **`.../sospot/backend`** ← 루트가 아닙니다 |
| Language | Java |
| Type | **Gradle - Groovy** |
| JDK | **17** (없으면 Download JDK → Zulu 17) |
| Group | `com.sospot` |
| Spring Boot | Initializr 기본 안정 버전 |
| Packaging | Jar |

**의존성**

- Spring Web
- Spring Data JPA
- PostgreSQL Driver
- Validation
- Lombok
- Spring Boot DevTools
- **Flyway Migration**

생성 후 `backend/build.gradle`의 `dependencies { }` **블록 안에** Caffeine을 추가합니다.

```groovy
implementation 'com.github.ben-manes.caffeine:caffeine'
```

그리고 Flyway 항목이 **두 줄 다** 있는지 확인하세요.

```groovy
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-database-postgresql'
```

Flyway 10부터 PostgreSQL 지원이 별도 모듈로 빠졌습니다. 두 번째 줄이 없으면 `Unsupported Database: PostgreSQL 16` 에러가 납니다.

> **Location을 루트로 잘못 잡았다면** 부록 A-1을 보세요.

---

## 3. IntelliJ 설정 4가지

`Ctrl+Alt+S`로 Settings를 엽니다. 검색창에 경로를 붙여넣으면 빨리 찾습니다.

| # | 경로 | 값 |
|---|---|---|
| ① | `Build, Execution, Deployment > Build Tools > Gradle` | Build and run using / Run tests using → **Gradle** |
| ② | `Build, Execution, Deployment > Compiler > Annotation Processors` | **Enable annotation processing** 체크 |
| ③ | `Editor > File Encodings` | Global·Project·Properties 전부 **UTF-8**, `Transparent native-to-ascii` **해제** |
| ④ | `Tools > Actions on Save` | Reformat code, Optimize imports 체크 |

**②를 빠뜨리면** Lombok의 `@Getter` 등이 전부 "cannot find symbol"로 뜹니다.

**③이 중요합니다.** BSI CSV가 cp949라 인코딩이 흔들리면 콘솔 출력이 깨져서 원인 파악이 안 됩니다.

**①은 원래 `IntelliJ IDEA`를 권했지만 Gradle이 맞습니다.** IntelliJ 컴파일러 모드에서 모듈 의존성이 깨져 빌드가 실패하는 경우가 있었습니다. 재실행이 조금 느려지지만 안정적입니다.

마지막으로 `File > Project Structure > Project`에서 **SDK 17**, Language level 17을 확인하세요.

---

## 4. 환경변수 파일 4개

비밀값은 IDE 설정이 아니라 **프로파일 파일**로 분리합니다. 플러그인이 필요 없고, 팀원이 클론하자마자 동일하게 동작합니다.

### ① `backend/src/main/resources/application.yml` (커밋)

Initializr가 만든 `application.properties`는 **삭제**하고 이걸 만드세요.

```yaml
spring:
  application:
    name: sospot
  profiles:
    active: local
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate.format_sql: true
  flyway:
    enabled: true
    baseline-on-migrate: true

llm:
  cache-ttl-minutes: 60

logging:
  level:
    org.hibernate.SQL: debug
```

`ddl-auto`는 반드시 **`validate`**. `update`로 두면 JPA가 스키마를 임의로 바꿔 Flyway와 충돌합니다.

### ② `application-local.yml` (커밋 안 함)

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

> **LLM 키는 지금 비워두세요.** D4(8/24~) 작업입니다. 빈 값이어도 부팅에는 지장 없습니다. 발급은 console.anthropic.com에서 하고, 선불 크레딧 충전과 spending limit 설정이 필요합니다.

### ③ `application-local.yml.example` (커밋)

②를 복사해 값만 비운 파일. 팀원은 이걸 복사해서 씁니다.

### ④ 루트 `.env` (커밋 안 함) — docker compose 전용

```
DB_USER=sospot
DB_PASSWORD=팀_공유_비밀번호
```

**②의 password와 ④의 `DB_PASSWORD`는 반드시 같아야 합니다.** 여기가 어긋나면 백엔드가 인증 실패로 죽습니다.

`.env.example`도 값만 비워 커밋하세요.

---

## 5. Docker로 DB 띄우기

### `docker-compose.yml` (루트)

```yaml
services:
  db:
    image: postgres:16-alpine
    container_name: sospot-db
    environment:
      POSTGRES_DB: sospot
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports: ["5432:5432"]
    volumes: ["pgdata:/var/lib/postgresql/data"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d sospot"]
      interval: 5s
      retries: 5

volumes:
  pgdata:
```

붙여넣은 뒤 **1번 줄이 `services:`, 2번 줄이 `  db:`** 인지 확인하세요. YAML은 들여쓰기가 문법이고 스페이스 2칸입니다.

### 기동

터미널에서:

```bash
docker compose up -d
docker compose ps
```

`STATUS`가 `Up ... (healthy)`면 성공입니다.

> **IntelliJ Services 탭으로도 실행할 수 있지만, 안 되면 미련 없이 터미널을 쓰세요.** IDE 연동은 편의 기능일 뿐이고 결과는 완전히 동일합니다. `client version 1.24 is too old` 오류가 대표적입니다(부록 A-4).

> **개발 중에는 DB만 컨테이너로 띄웁니다.** 백엔드·프론트까지 컨테이너화하면 코드 한 줄 고칠 때마다 이미지를 다시 빌드해야 합니다. 전체 컨테이너화는 배포(D5) 때만 합니다.

---

## 6. Flyway 마이그레이션 작성 + 백엔드 실행

### 6.1 폴더 만들기

`backend/src/main/resources/` 우클릭 → `New > Directory` → 이름에 **`db/migration`** (슬래시 포함)을 그대로 입력합니다.

> `db.migration`이라는 **한 개 폴더**가 되면 Flyway가 영영 못 찾습니다. 에러도 없이 조용히 넘어가니 주의하세요. 트리에서 한 줄로 보이는 건 IntelliJ의 폴더 압축 표시일 수 있으니, 확인하려면 Project 창 `⚙ > Tree Appearance > Compact Middle Packages` 체크를 해제하세요.

### 6.2 SQL 파일 3개

**파일명 언더스코어는 2개입니다.** `V1_init...`처럼 1개면 인식되지 않습니다.

**`V1__init_dimensions.sql`**

```sql
CREATE TABLE dim_period (
    period_id   CHAR(6) PRIMARY KEY,
    year        SMALLINT NOT NULL,
    quarter     SMALLINT NOT NULL,
    base_date   DATE     NOT NULL
);

CREATE TABLE dim_dong (
    dong_code   CHAR(8) PRIMARY KEY,
    sigungu     VARCHAR(20) NOT NULL,
    dong_name   VARCHAR(40) NOT NULL,
    center_lat  NUMERIC(10,7),
    center_lng  NUMERIC(10,7)
);

CREATE TABLE dim_category (
    cat_code    VARCHAR(6) PRIMARY KEY,
    cat_name    VARCHAR(60) NOT NULL,
    parent_code VARCHAR(6),
    cat_level   VARCHAR(6) NOT NULL,
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_code) REFERENCES dim_category(cat_code)
);

CREATE INDEX idx_category_level ON dim_category(cat_level);
```

**`V2__fact_tables.sql`**

```sql
CREATE TABLE fact_store_count (
    dong_code   CHAR(8)    NOT NULL REFERENCES dim_dong(dong_code),
    cat_code    VARCHAR(6) NOT NULL REFERENCES dim_category(cat_code),
    period_id   CHAR(6)    NOT NULL REFERENCES dim_period(period_id),
    cat_level   VARCHAR(6) NOT NULL,
    store_count INTEGER    NOT NULL,
    PRIMARY KEY (dong_code, cat_code, period_id)
);

CREATE INDEX idx_store_count_lookup ON fact_store_count(period_id, cat_level);

CREATE TABLE fact_anomaly (
    dong_code           CHAR(8)      NOT NULL REFERENCES dim_dong(dong_code),
    cat_code            VARCHAR(6)   NOT NULL REFERENCES dim_category(cat_code),
    period_id           CHAR(6)      NOT NULL REFERENCES dim_period(period_id),
    cat_level           VARCHAR(6)   NOT NULL,
    store_count         INTEGER      NOT NULL,
    growth_rate         NUMERIC(8,5),
    city_growth_rate    NUMERIC(8,5),
    relative_gap        NUMERIC(8,5),
    cum_change_rate     NUMERIC(8,5),
    consecutive_decline BOOLEAN      NOT NULL DEFAULT FALSE,
    sample_size_flag    VARCHAR(4)   NOT NULL DEFAULT 'OK',
    score               NUMERIC(6,3),
    grade               VARCHAR(10),
    PRIMARY KEY (dong_code, cat_code, period_id)
);

CREATE INDEX idx_anomaly_rank ON fact_anomaly(period_id, cat_level, score DESC);

CREATE TABLE fact_dong_score (
    dong_code         CHAR(8)    NOT NULL REFERENCES dim_dong(dong_code),
    period_id         CHAR(6)    NOT NULL REFERENCES dim_period(period_id),
    raw_score         NUMERIC(6,3),
    pct_score         NUMERIC(6,3),
    grade             VARCHAR(10),
    anomaly_cat_count SMALLINT   NOT NULL DEFAULT 0,
    valid_cat_count   SMALLINT   NOT NULL DEFAULT 0,
    PRIMARY KEY (dong_code, period_id)
);
```

**`V3__fact_bsi.sql`**

```sql
CREATE TABLE fact_bsi (
    period_month CHAR(7)     NOT NULL,
    metric_name  VARCHAR(40) NOT NULL,
    value        NUMERIC(6,2),
    PRIMARY KEY (period_month, metric_name)
);
```

> **세 파일 모두 첫 줄이 `CREATE TABLE`로 시작해야 합니다.** 복사할 때 마크다운 백틳이나 `#`이 딸려 들어가면 `syntax error at or near "#"`로 실패합니다. SQL 주석은 `#`이 아니라 `--`입니다.
>
> **V2·V3를 빈 파일로 두면 안 됩니다.** Flyway는 빈 마이그레이션을 에러 없이 통과시키므로, 테이블이 안 만들어진 채로 넘어갑니다.

### 6.3 실행

`SospotApplication`을 실행합니다. 콘솔에 아래가 뜨면 성공입니다.

```
HikariPool-1 - Start completed.
Database: jdbc:postgresql://localhost:5432/sospot (PostgreSQL 16.x)
Migrating schema "public" to version "1 - init dimensions"
Migrating schema "public" to version "2 - fact tables"
Migrating schema "public" to version "3 - fact bsi"
Successfully applied 3 migrations
Tomcat started on port 8080 (http)
Started SospotApplication in 10.6 seconds
```

첫 실행은 10초 안팎 걸립니다. 이후에는 3~5초로 줄어듭니다. **이 상태는 "끝난" 게 아니라 서버가 켜져 있는 상태**입니다. 끄려면 Run 창의 빨간 정지 버튼을 누르세요.

브라우저로 `http://localhost:8080`을 열면 에러 페이지가 뜨는데, 아직 컨트롤러가 없으니 정상입니다.

> 마이그레이션이 실패해도 자동 롤백되므로 DB를 지울 필요 없습니다. SQL을 고치고 재실행하면 됩니다.
>
> **단, 한 번 성공한 마이그레이션은 절대 수정하지 마세요.** 체크섬이 달라져 부팅이 실패합니다. 변경이 필요하면 `V4__...sql`을 새로 만듭니다.

---

## 7. DataGrip 연결

**테이블이 만들어진 뒤에 연결**해야 바로 확인이 됩니다. 그래서 이 순서입니다.

1. `+` → `Data Source` → `PostgreSQL`
2. 하단에 `Download missing driver files`가 뜨면 클릭
3. 값 입력

| 항목 | 값 |
|---|---|
| Host / Port | `localhost` / `5432` |
| User | `sospot` |
| Password | `.env`의 값 |
| Database | `sospot` |

4. `Test Connection` → 초록 체크 → `OK`

**테이블 8개**가 보이면 완료입니다.

```
dim_category, dim_dong, dim_period
fact_anomaly, fact_bsi, fact_dong_score, fact_store_count
flyway_schema_history
```

안 보이면 데이터 소스 우클릭 → `Properties` → `Schemas` 탭에서 `sospot > public` 체크.

### 검증 쿼리 저장

`backend/sql/validation.sql`로 저장해 **커밋하세요.** 파이프라인을 돌린 뒤 이 숫자와 다르면 즉시 버그입니다.

```sql
-- ① 분기별 총 점포수: 78246 / 78607 / 80704
SELECT period_id, SUM(store_count) AS total
FROM fact_store_count WHERE cat_level = 'MAJOR'
GROUP BY period_id ORDER BY period_id;

-- ② 행정동 수: 82
SELECT COUNT(*) FROM dim_dong;

-- ③ 업종 마스터: MAJOR 10, MIDDLE 74
SELECT cat_level, COUNT(*) FROM dim_category GROUP BY cat_level;

-- ④ 행정동 등급 분포: 중점검토 9 / 주의 16 / 관심 25 / 정상 32
SELECT grade, COUNT(*) FROM fact_dong_score
WHERE period_id = '202606' GROUP BY grade;

-- ⑤ 상위 이상징후: 판암1동 과학·기술이 1위
SELECT d.dong_name, c.cat_name, a.store_count, a.relative_gap, a.score
FROM fact_anomaly a
JOIN dim_dong d ON d.dong_code = a.dong_code
JOIN dim_category c ON c.cat_code = a.cat_code
WHERE a.period_id = '202606' AND a.cat_level = 'MAJOR' AND a.sample_size_flag = 'OK'
ORDER BY a.score DESC LIMIT 10;

-- ⑥ 마이그레이션 이력
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```

지금은 ②③이 0이고 ⑥만 3건 나옵니다. 정상입니다.

---

## 8. Python 전처리 모듈

`Settings > Plugins`에서 **Python** 플러그인 설치 후,
`File > Project Structure > Modules > + > Python`

| 필드 | 값 |
|---|---|
| Name | `pipeline` |
| Location | **부모 디렉터리** (`...\sospot`) — `sospot\pipeline`을 넣으면 `pipeline\pipeline`이 됩니다 |
| Environment | New |
| Environment type | Virtualenv |
| Location (venv) | 자동값 `...\pipeline\.venv` |
| Base interpreter | Python 3.11 |

창 아래 `Module will be created in:` 이 `...\sospot\pipeline`인지 확인하고 Create.

> `Directory pipeline is not empty` 경고는 무시하세요. 기존 폴더를 정확히 가리킨다는 뜻이고, 안의 파일은 지워지지 않습니다.

**생성 후 OK 누르기 전에 두 가지**

- `Sources` 탭에서 `pipeline/src`를 선택하고 **`Sources`** 버튼 클릭 (Java와 달리 Python은 수동 지정)
- `Dependencies` 탭에서 Module SDK가 `Python 3.11 (pipeline)`인지 확인

**`pipeline/requirements.txt`**

```
pandas==2.2.*
psycopg2-binary
SQLAlchemy
python-dotenv
```

파일을 만들면 상단에 노란 배너로 설치 제안이 뜹니다. 클릭하면 venv에 설치됩니다.

**스크립트 구성** (D2에서 작성)

```
pipeline/src/
├── load.py         # CSV 로드 + 9개 컬럼 추출 + 검증
├── dimensions.py   # dim_dong, dim_category 생성
├── aggregate.py    # fact_store_count (대분류/중분류 2단)
├── metrics.py      # fact_anomaly, fact_dong_score
├── bsi.py          # BSI cp949 로드 + long 변환
└── report.py       # 검증 리포트 출력
```

**인코딩 주의**: 상가정보는 `utf-8`, BSI는 **`cp949`**입니다.

---

## 9. 프론트엔드

`frontend/` 디렉터리에서:

```bash
npm create vite@latest . -- --template react
npm i react-leaflet leaflet recharts axios react-router-dom
```

> `npm create vite`가 설치와 실행까지 이어서 진행합니다. `VITE ready in ...`이 뜨고 멈춘 것처럼 보이면 **개발 서버가 실행 중인 정상 상태**입니다. `Ctrl+C`로 끕니다.

> TypeScript는 담당자가 익숙한 경우에만. 17일 동안 타입 에러와 싸울 여유는 없습니다.

**`vite.config.js`** — CORS를 프록시로 우회합니다.

```js
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { '/api': 'http://localhost:8080' }
  }
})
```

프론트에서는 `axios.get('/api/anomalies')`처럼 상대 경로만 씁니다.

**`src/main.jsx` 최상단**

```js
import 'leaflet/dist/leaflet.css'
```

빠뜨리면 지도가 회색 조각으로 깨지는데 원인 찾기가 까다롭습니다.

---

## 10. 첫 커밋

여기까지가 **팀원이 클론해서 그대로 재현할 수 있는 첫 지점**입니다. 반드시 커밋하고 넘어가세요.

커밋 전 확인:

- [ ] `.env`가 스테이징에 없는가
- [ ] `application-local.yml`이 스테이징에 없는가
- [ ] `.env.example`, `application-local.yml.example`은 포함됐는가

---

## 11. HTTP Client 준비 (권장)

Ultimate 내장 기능입니다. `backend/http/analysis.http`를 만들어 두세요.

```http
### 이상징후 지역 조회
GET http://localhost:8080/api/anomalies?period=202606&catLevel=MAJOR&topN=10

### 행정동 상세
GET http://localhost:8080/api/regions/30230560?period=202606
```

Postman이 필요 없고, **파일로 커밋되므로 프론트 담당자가 API 스펙을 코드처럼 읽습니다.** 2인 팀에서 API 문서를 따로 안 써도 되는 게 큽니다.

---

## 부록 A. 트러블슈팅 (실제로 걸렸던 것들)

### A-1. Gradle 프로젝트를 루트에 만들었을 때

`src/`, `build.gradle`이 `frontend/`와 같은 층에 생긴 경우. **지우지 말고 옮깁니다.**

1. `File > Close Project`
2. 탐색기에서 `backend/`로 이동: `src/` · `build.gradle` · `settings.gradle` · `gradlew` · `gradlew.bat` · `gradle/` · `.gitattributes`
3. 루트의 `.gradle/` 폴더와 `sospot.iml` 삭제
4. `backend/settings.gradle`의 `rootProject.name`을 `'sospot-backend'`로 변경
5. IntelliJ에서 **루트 폴더**를 Open
6. `backend/build.gradle` 우클릭 → **Link Gradle Project**

7번을 하면 `backend/src/main/java`가 자동으로 소스 루트(파란 폴더)가 됩니다. 수동으로 Mark as Sources Root 할 필요 없습니다.

### A-2. `Directory ... does not contain a Gradle build`

`.idea/gradle.xml`에 옛 경로 링크가 남은 경우입니다.

Gradle 창에서 루트를 가리키는 항목 우클릭 → **`Unlink Gradle Project`**.
항목이 안 보이면 `.idea/gradle.xml`에서 `externalProjectPath`가 `$PROJECT_DIR$`인 `<GradleProjectSettings>` 블록을 통째로 삭제.

### A-3. `package org.springframework.boot does not exist`

의존성은 받아졌는데 컴파일만 실패하는 경우. IntelliJ 모듈 설정이 깨진 상태입니다.

1. `Build and run using`을 **Gradle**로 변경 (3장 ①)
2. 안 되면 `File > Invalidate Caches... > Invalidate and Restart`
3. 그래도 안 되면 터미널: `cd backend && .\gradlew bootRun`

### A-4. Docker `client version 1.24 is too old`

IntelliJ가 Docker 데몬 소켓을 못 잡은 경우입니다.

1. Docker Desktop이 `Engine running`인지 확인
2. `Settings > Build, Execution, Deployment > Docker`에서 `Docker for Windows` 선택
3. Docker Desktop을 먼저 켜고 IntelliJ 재시작
4. **5분 안에 안 풀리면 터미널로 우회하세요.** 결과는 동일합니다

### A-5. `docker-compose.yml`에 `services:`가 두 번

붙여넣기 시 기존 줄 아래에 통째로 넣어 중첩된 경우. 전체 선택 후 다시 붙여넣으세요. 손으로 들여쓰기를 맞추는 것보다 빠릅니다.

### A-6. `Could not find method implementation()`

`implementation`을 `dependencies { }` **블록 밖**에 쓴 경우. 블록 안으로 옮기세요.

### A-7. Flyway `syntax error at or near "#"`

SQL 파일 첫 줄에 마크다운 잔재가 들어간 경우. 첫 줄이 `CREATE TABLE`로 시작하도록 정리하고 재실행하면 됩니다. 실패한 마이그레이션은 자동 롤백되므로 DB를 지울 필요 없습니다.

### A-8. DB 비밀번호를 바꿨는데 반영이 안 됨

`POSTGRES_PASSWORD`는 볼륨이 처음 만들어질 때만 적용됩니다.

```bash
docker compose down -v
docker compose up -d
```

### A-9. `port is already allocated`

로컬에 PostgreSQL이 설치돼 5432가 점유된 경우. compose의 포트를 `"5433:5432"`로 바꾸고 `application-local.yml`의 URL도 `localhost:5433`으로 맞추세요.

---

## 부록 B. 일상 작업 순서

```bash
# 1. DB 기동 (PC 재부팅 후 매번 필요)
docker compose up -d

# 2. 백엔드 — IntelliJ에서 SospotApplication 실행, 또는
cd backend && .\gradlew bootRun

# 3. 프론트
cd frontend && npm run dev        # http://localhost:5173

# 4. 종료
docker compose down               # 데이터는 볼륨에 유지됨
```

백엔드 8080 · 프론트 5173 · DB 5432. 프론트의 `/api` 요청은 Vite 프록시가 8080으로 넘깁니다.

---

## 부록 C. 팀원 온보딩 (클론 후)

1. IntelliJ에서 **루트 폴더** Open
2. `backend/build.gradle` 우클릭 → Link Gradle Project
3. 3장의 설정 4가지 적용
4. `application-local.yml.example` 복사 → `application-local.yml` → 값 입력
5. `.env.example` 복사 → `.env` → **같은 DB 비밀번호** 입력
6. `docker compose up -d`
7. `SospotApplication` 실행 → 마이그레이션 자동 적용
8. `cd frontend && npm install && npm run dev`
9. `pipeline/` 모듈에 Python SDK 지정 후 requirements 설치

---

## 다음 할 일

- [ ] **행정동 경계 GeoJSON 확보** (행정안전부) — **8/16까지. 미확보 시 좌표 버블맵 전환 판단**
- [ ] `pipeline/src/` 전처리 스크립트 6종 (D2)
- [ ] 분석 API 5종 (D2)
- [ ] (선택) 패키지명 `org.example.sospot` → `com.sospot` — 파일이 적은 지금이 가장 쌉니다
