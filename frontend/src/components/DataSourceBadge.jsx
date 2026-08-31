import { formatPeriod } from '../lib/periodFormat.js'

function DataSourceBadge({ period = '최신 분석 완료 분기' }) {
  return (
    <section className="data-source" aria-label="데이터 정보">
      <span className="data-source__label">데이터 기준</span>
      <span>{formatPeriod(period)}</span>
      <span className="data-source__divider" aria-hidden="true" />
      <span className="data-source__label">출처</span>
      <span>
        소상공인시장진흥공단 상가(상권)정보 · 소상공인시장진흥공단 소상공인 경기동향(BSI) · vuski/admdongkor 행정동 경계 (통계청 SGIS 기반)
      </span>
    </section>
  )
}

export default DataSourceBadge
