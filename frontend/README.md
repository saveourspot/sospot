# SOSpot Frontend

대전 행정동별 상권 이상징후와 정책 검토 근거를 보여주는 React 웹 화면입니다. Vite, React Router, Leaflet, Recharts, Axios를 사용합니다.

## 실행

Node.js 20.19 이상 또는 22.12 이상이 필요합니다.

```powershell
npm install
npm run dev
```

개발 서버는 기본적으로 `http://localhost:5173`에서 실행되며 `/api` 요청을 `http://localhost:8080`의 Backend로 전달합니다.

## 주요 화면

- `/`: 서비스 소개와 최신 분석 요약
- `/anomaly-map`: 업종 필터가 적용된 행정동 이상징후 지도
- `/regions/:dongCode`: 성장 모멘텀, 업종별 상대격차, 추세, 유사 상권 벤치마킹
- `/ai`: 실제 분석 API에 근거한 자연어 질의응답

## 검증

```powershell
npm run build
npm run lint
```

등급은 절대 위험도가 아닌 대전 내 상대적인 검토 우선순위입니다. 점포 수 감소를 개별 점포의 폐업으로 해석하지 않습니다.
