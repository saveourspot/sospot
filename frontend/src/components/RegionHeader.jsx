import GradeBadge from './GradeBadge.jsx'

function RegionHeader({ header, periodLabel }) {
  const hasGrade = Boolean(header.grade)
  const hasScore = Number.isFinite(header.pctScore)
  const hasRank = Number.isInteger(header.rank) && Number.isInteger(header.totalDongCount)

  return (
    <header className="region-header">
      <div className="region-header__title-row">
        <div>
          <p className="eyebrow">{header.sigungu} 행정동 분석</p>
          <h1>{header.dongName}</h1>
          <p className="region-header__period">{periodLabel}</p>
        </div>
        {hasGrade ? (
          <GradeBadge grade={header.grade} />
        ) : (
          <span className="pending-badge">등급 데이터 없음</span>
        )}
      </div>

      <dl className="region-header__metrics">
        <div>
          <dt>행정동 종합 백분위</dt>
          <dd>{hasScore ? `${header.pctScore.toFixed(1)}점` : '데이터 없음'}</dd>
        </div>
        <div>
          <dt>대전 내 검토 순위</dt>
          <dd>
            {hasRank ? `${header.rank}위 / ${header.totalDongCount}개 동` : '데이터 없음'}
          </dd>
        </div>
        <div>
          <dt>행정동 코드</dt>
          <dd>{header.dongCode}</dd>
        </div>
      </dl>
    </header>
  )
}

export default RegionHeader
