import { Link } from 'react-router-dom'
import Empty from './Empty.jsx'
import GradeBadge from './GradeBadge.jsx'

function TopRegionList({ regions = [] }) {
  const topRegions = regions.slice(0, 5)

  if (topRegions.length === 0) {
    return (
      <Empty message="분석 API 연결 후 우선검토 지역 TOP 5가 표시됩니다." />
    )
  }

  return (
    <ol className="top-region-list">
      {topRegions.map((region, index) => (
        <li key={region.dongCode}>
          <Link
            className="top-region-item"
            to={`/regions/${region.dongCode}`}
            aria-label={`${index + 1}위 ${region.dongName} 상세 보기`}
          >
            <span className="top-region-item__rank">{index + 1}</span>
            <span className="top-region-item__name">{region.dongName}</span>
            <GradeBadge grade={region.grade} />
            {Number.isFinite(region.pctScore) && (
              <span className="top-region-item__score">
                {region.pctScore.toFixed(1)}점
              </span>
            )}
            <span className="top-region-item__arrow" aria-hidden="true">→</span>
          </Link>
        </li>
      ))}
    </ol>
  )
}

export default TopRegionList
