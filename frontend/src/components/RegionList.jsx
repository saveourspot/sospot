import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { GRADE_ORDER } from '../lib/gradeStyles.js'
import GradeBadge from './GradeBadge.jsx'

const gradeRank = new Map(GRADE_ORDER.map((grade, index) => [grade, index]))

function RegionList({ regions, highlightedDongCode, onHighlight, emptyMessage }) {
  const itemRefs = useRef(new Map())
  const sortedRegions = [...regions].sort((left, right) => {
    const gradeDifference =
      (gradeRank.get(left.grade) ?? GRADE_ORDER.length) -
      (gradeRank.get(right.grade) ?? GRADE_ORDER.length)

    return gradeDifference || left.dongName.localeCompare(right.dongName, 'ko')
  })

  useEffect(() => {
    if (highlightedDongCode) {
      itemRefs.current
        .get(highlightedDongCode)
        ?.scrollIntoView({ block: 'nearest' })
    }
  }, [highlightedDongCode])

  return (
    <aside className="region-list-panel" aria-labelledby="region-list-heading">
      <div className="region-list-panel__heading">
        <div>
          <h2 id="region-list-heading">행정동 목록</h2>
          <p>등급순 · {sortedRegions.length}개</p>
        </div>
        <span>분석 결과</span>
      </div>
      {sortedRegions.length === 0 ? (
        <p className="region-list-panel__empty">
          {emptyMessage ?? '선택된 자치구가 없습니다.'}
        </p>
      ) : (
        <ol className="region-list">
          {sortedRegions.map((region) => {
            const isHighlighted = highlightedDongCode === region.dongCode

            return (
              <li
                key={region.dongCode}
                ref={(element) => {
                  if (element) {
                    itemRefs.current.set(region.dongCode, element)
                  } else {
                    itemRefs.current.delete(region.dongCode)
                  }
                }}
                className={isHighlighted ? 'is-highlighted' : ''}
                onMouseEnter={() => onHighlight(region.dongCode)}
                onMouseLeave={() => onHighlight(null)}
              >
                <Link
                  to={`/regions/${region.dongCode}`}
                  onFocus={() => onHighlight(region.dongCode)}
                  onBlur={() => onHighlight(null)}
                >
                  <span className="region-list__name">{region.dongName}</span>
                  <span className="region-list__sigungu">{region.sigungu}</span>
                  <GradeBadge grade={region.grade} />
                  <span className="region-list__arrow" aria-hidden="true">→</span>
                </Link>
              </li>
            )
          })}
        </ol>
      )}
    </aside>
  )
}

export default RegionList
