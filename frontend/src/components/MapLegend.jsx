import { GRADE_COLORS, GRADE_ORDER, NO_DATA_COLOR } from '../lib/gradeStyles.js'

function MapLegend() {
  return (
    <aside className="map-legend" aria-label="지도 등급 범례">
      <strong>검토 우선순위</strong>
      <ul>
        {GRADE_ORDER.map((grade) => (
          <li key={grade}>
            <span style={{ backgroundColor: GRADE_COLORS[grade] }} />
            {grade}
          </li>
        ))}
        <li>
          <span style={{ backgroundColor: NO_DATA_COLOR }} />
          표본 부족·데이터 없음
        </li>
      </ul>
      <p>대전 내 상대적 순위이며 절대 위험도를 의미하지 않습니다.</p>
    </aside>
  )
}

export default MapLegend
