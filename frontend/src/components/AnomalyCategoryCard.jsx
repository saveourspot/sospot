function formatPercent(value, suffix = '%') {
  if (!Number.isFinite(value)) {
    return '—'
  }

  const percent = value * 100
  return `${percent > 0 ? '+' : ''}${percent.toFixed(1)}${suffix}`
}

function Sparkline({ points }) {
  const counts = points.map((point) => point.count)
  const min = Math.min(...counts)
  const max = Math.max(...counts)
  const range = max - min || 1
  const coordinates = counts
    .map((count, index) => {
      const x = counts.length === 1 ? 50 : (index / (counts.length - 1)) * 100
      const y = 34 - ((count - min) / range) * 28
      return `${x},${y}`
    })
    .join(' ')

  return (
    <div className="anomaly-sparkline" aria-label="3분기 점포 수 추이">
      <svg viewBox="0 0 100 40" role="img" aria-hidden="true">
        <polyline points={coordinates} />
      </svg>
      <div>
        {points.map((point) => (
          <span key={point.period}>
            <small>{formatPeriod(point.period)}</small>
            <strong>{point.count}개</strong>
          </span>
        ))}
      </div>
    </div>
  )
}

function AnomalyCategoryCard({ rank, anomaly }) {
  if (!anomaly) {
    return (
      <article className="anomaly-card anomaly-card--pending">
        <div className="anomaly-card__heading">
          <span>{rank}위</span>
          <strong>표시할 분석 결과 없음</strong>
        </div>
        <div className="anomaly-card__placeholder" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
        <p>이 업종의 유효한 분석 결과를 확인할 수 없습니다.</p>
      </article>
    )
  }

  const storeCounts = anomaly.storeCounts ?? []

  return (
    <article className="anomaly-card">
      <div className="anomaly-card__heading">
        <span>{rank}위</span>
        <strong>{anomaly.catName}</strong>
      </div>

      {storeCounts.length > 0 && <Sparkline points={storeCounts} />}

      <dl className="anomaly-card__metrics">
        <div>
          <dt>지역 증감률</dt>
          <dd>{formatPercent(anomaly.growthRate)}</dd>
        </div>
        <div>
          <dt>대전 증감률</dt>
          <dd>{formatPercent(anomaly.cityGrowthRate)}</dd>
        </div>
        <div className="anomaly-card__relative-gap">
          <dt>상대격차</dt>
          <dd>{formatPercent(anomaly.relativeGap, '%p')}</dd>
        </div>
      </dl>
    </article>
  )
}

export default AnomalyCategoryCard
import { formatPeriod } from '../lib/periodFormat.js'
