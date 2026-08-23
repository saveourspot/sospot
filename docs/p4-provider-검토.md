# P4-00 AI Provider 검토 (팀 회의 안건)

> 작성: 민솔, 2026-08-23
> 목적: P4-01 착수 전 **MVP 단일 provider** 확정. 복수 provider 추상화 금지 (조율이력 2차 합의).
> 결정 기한: 8/25 P4 착수 전
> 결정 후 이 파일은 삭제하지 말고 근거 아카이브로 남긴다.

---

## 1. 요구사항 (개발조율안·CLAUDE.md 기반)

| 항목 | 요구 |
|---|---|
| Tool Calling | **필수**. searchAnomalyRegions / getRegionDetail / compareRegions 3종 (P4-03) |
| 통합 방식 | **WebClient REST 직접 호출** (SDK 금지, CLAUDE.md §4) + Caffeine 캐시 |
| 언어 | 한국어 자연스러운 서술형 응답 |
| 응답 시간 | 챗봇 UX 감내 수준 (~3초 이내 선호) |
| 가드레일 | 도구 결과 밖 수치 생성 금지 · 등급 4개 명칭 준수 · 결측 케이스 고정 안내 |
| 등록/승인 | **8/31 마감 · 8/25 착수** 감안, 지금부터 승인 대기 리스크 있는 provider는 위험 |
| 예산 | 데모용 트래픽 (수십~수백 호출/일). 저비용이 절대적 조건은 아님 |

---

## 2. 후보 비교

### 2.1 Anthropic Claude (Haiku 4.5 또는 Sonnet 4.6)

| 항목 | 평가 |
|---|---|
| Tool Calling | ✅ Native, JSON schema로 정의. 안정적 |
| REST 직접 호출 | ✅ `/v1/messages` 엔드포인트, 표준 HTTP |
| 한국어 | ✅ 우수 (Sonnet ≈ 원어민 수준, Haiku 자연스러움) |
| 응답 시간 | Haiku ≈ 0.5~1s, Sonnet ≈ 1.5~2.5s |
| 등록 | ✅ 즉시 (팀이 Claude Code 사용 중, 계정 재활용 가능) |
| 비용 (1M tokens) | Haiku: in $1 / out $5 · Sonnet: in $3 / out $15 |
| 리스크 | 없음. 팀이 이미 API 사용 경험 있음 |

### 2.2 OpenAI (gpt-4o-mini 또는 gpt-4.1)

| 항목 | 평가 |
|---|---|
| Tool Calling | ✅ Native functions, 성숙 |
| REST 직접 호출 | ✅ `/v1/chat/completions` |
| 한국어 | ✅ 양호 (4o-mini도 실용 수준) |
| 응답 시간 | 4o-mini ≈ 0.5s, 4.1 ≈ 1.5s |
| 등록 | ✅ 즉시 (신규 계정 + 카드 등록 필요) |
| 비용 (1M tokens) | 4o-mini: in $0.15 / out $0.60 · 4.1: in $2 / out $8 |
| 리스크 | Claude 대비 tool 결과 무시하고 hallucination하는 경향 다소 있음 → 가드레일 강화 필요 |

### 2.3 Naver HyperCLOVA X

| 항목 | 평가 |
|---|---|
| Tool Calling | ✅ Function calling 지원 |
| REST 직접 호출 | ✅ NCP 콘솔 API |
| 한국어 | ✅ **최상급** (국내 발화체·행정용어 강점) |
| 응답 시간 | 1~2s |
| 등록 | ⚠️ **NCP 계정 + 서비스 신청 절차**. 승인 지연 가능. 8/25까지 준비 못 하면 blocker |
| 비용 | 유사 수준, 무료 크레딧 제공 |
| 리스크 | 승인 대기·문서 진입 장벽·팀 경험 부재 |

### 2.4 Upstage Solar (Pro)

| 항목 | 평가 |
|---|---|
| Tool Calling | ✅ OpenAI 호환 스펙 |
| REST 직접 호출 | ✅ |
| 한국어 | ✅ 우수 (국내 파인튜닝) |
| 응답 시간 | ~1s |
| 등록 | ✅ 즉시 (Upstage Console) |
| 비용 | OpenAI 대비 저렴 |
| 리스크 | Tool calling 실제 안정성 팀 미검증. 문서상 지원과 실제 동작 갭 가능 |

### 2.5 Google Gemini (1.5 Flash / 2.0 Flash)

| 항목 | 평가 |
|---|---|
| Tool Calling | ✅ Function calling |
| REST 직접 호출 | ✅ `generativelanguage.googleapis.com` |
| 한국어 | ✅ 양호 |
| 응답 시간 | Flash 계열 매우 빠름 (~0.5s) |
| 등록 | ✅ 즉시 (Google AI Studio) |
| 비용 | Flash: 매우 저렴 |
| 리스크 | Tool calling 스키마가 다른 provider와 살짝 다름 (OpenAPI subset) |

---

## 3. 권고

### 1순위: **Anthropic Claude Haiku 4.5**

근거:
1. **팀 경험**: 프로젝트 자체가 Claude Code로 진행 중. 프롬프트 튜닝·응답 형태 이미 익숙
2. **Tool 결과 준수도 최상**: 하드코딩 fallback을 폐기하고 deterministic template로 간 시나리오(조율이력 2차)에서 tool 결과를 벗어난 hallucination이 가장 적은 provider가 유리. Claude가 이 지점에서 검증된 강점
3. **등록/승인 리스크 없음**: 계정 재활용 가능. 8/25 착수 확실
4. **한국어**: Haiku도 서비스 안내 수준 충분히 자연스러움. 사업계획서·데모에서 위화감 없음
5. **비용**: 데모 수준 트래픽에서 무의미한 차이

### 2순위: **OpenAI gpt-4o-mini**

Claude 이용 불가 상황(예: 팀 API 크레딧 소진) 시 즉시 스위치 가능. Tool calling 성숙도 최상. 다만 가드레일 프롬프트를 더 방어적으로 써야 함.

### 배제 (이번 라운드)

- **HyperCLOVA X**: 승인 지연 리스크. 8/25 데드라인 위험
- **Upstage Solar**: 팀 검증 이력 없음, tool calling 실동작 확인 시간 부족
- **Gemini**: 특별한 이점 없음 (Claude/OpenAI 대비 tool 스키마만 다름)

---

## 4. 결정 후 후속 조치

- **API Key 관리**: `application-local.yml`에만 저장, 커밋 금지. 예시는 `application-local.yml.example`에 placeholder
- **P4-01**: WebClient + Caffeine + 선정된 provider 엔드포인트로 세팅
- **P4-03**: 해당 provider의 tool 스키마 형식으로만 정의 (추상화 금지)
- **P4-09**: LLM 응답 실패 시 도구 결과를 템플릿으로 서술하는 deterministic fallback

---

## 5. 회의에서 결정할 것

- [ ] Provider 확정 (권고: Claude Haiku 4.5)
- [ ] API Key 발급 담당·저장 위치
- [ ] 모델명 확정 (Haiku vs Sonnet — 서술 품질 vs 비용/속도 tradeoff)
- [ ] 캐시 TTL 확정 (`application.yml` 현재 60분)
