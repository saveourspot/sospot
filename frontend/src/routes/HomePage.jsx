import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import AiPromptBox from '../components/AiPromptBox.jsx'
import ErrorState from '../components/ErrorState.jsx'
import Loading from '../components/Loading.jsx'
import SummaryCard from '../components/SummaryCard.jsx'
import TopRegionList from '../components/TopRegionList.jsx'
import { getSummary } from '../lib/api.js'
import { formatPeriod } from '../lib/periodFormat.js'

function formatPercent(value) {
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue)) return '데이터 없음'

  const percent = numericValue * 100
  return `${percent > 0 ? '+' : ''}${percent.toFixed(1)}`
}

function formatPeriodLabel(period, comparisonPeriods) {
  if (!period || comparisonPeriods.length === 0) {
    return '분석 기준 시점을 확인할 수 없습니다.'
  }

  const year = period.slice(0, 4)
  const month = Number(period.slice(4, 6))
  const quarter = Math.ceil(month / 3)
  const comparison = comparisonPeriods
    .map(formatPeriod)
    .join('~')

  return `${year}년 ${quarter}분기 · ${comparison} 비교`
}

function HomePage() {
  const [summary, setSummary] = useState(null)
  const [error, setError] = useState('')
  const [loadAttempt, setLoadAttempt] = useState(0)

  useEffect(() => {
    let ignore = false

    async function loadSummary() {
      setSummary(null)
      setError('')

      try {
        const result = await getSummary()
        if (!result?.data || !Array.isArray(result.comparisonPeriods)) {
          throw new Error('분석 요약 응답 형식을 확인할 수 없습니다.')
        }
        if (!ignore) setSummary(result)
      } catch (requestError) {
        if (!ignore) {
          setError(
            requestError.response?.data?.message ||
              requestError.message ||
              '최근 분석 요약을 불러오지 못했습니다.',
          )
        }
      }
    }

    loadSummary()
    return () => {
      ignore = true
    }
  }, [loadAttempt])

  const data = summary?.data
  const priorityCount = data?.gradeCounts?.중점검토 ?? 0
  const cautionCount = data?.gradeCounts?.주의 ?? 0

  return (
    <main>
      <section className="home-hero">
        <div className="home-hero__content">
          <p className="eyebrow">대전 소상공인 상권 변화 모니터링</p>
          <h1>
            상권의 작은 변화를,
            <br />
            <em>놓치지 않도록.</em>
          </h1>
          <p className="home-hero__description">
            SOSpot은 행정동별 점포 수 변화를 대전 전체 동일 업종 흐름과
            비교하여, 정책 담당자가 우선 검토할 후보를 찾도록 돕습니다.
          </p>
          <div className="home-hero__actions">
            <Link className="primary-button" to="/map">
              이상징후 지도 보기
            </Link>
            <span>예측이 아닌 최근 데이터 기반 상대 비교 분석입니다.</span>
          </div>
          <div className="home-hero__meta" aria-label="서비스 분석 범위">
            <span><strong>82</strong>개 행정동</span>
            <span><strong>10</strong>개 대분류 업종</span>
            <span><strong>3</strong>개 분기 비교</span>
          </div>
        </div>
        <div className="home-hero__visual" aria-hidden="true">
          <div className="home-hero__visual-header">
            <span>대전 상권 시그널</span>
            <i>MONITOR</i>
          </div>
          <div className="home-hero__signal">
            <span className="home-hero__signal-ring home-hero__signal-ring--outer" />
            <span className="home-hero__signal-ring home-hero__signal-ring--inner" />
            <span className="home-hero__signal-sweep" />
            <span className="home-hero__signal-dot home-hero__signal-dot--one" />
            <span className="home-hero__signal-dot home-hero__signal-dot--two" />
            <span className="home-hero__signal-dot home-hero__signal-dot--three" />
            <strong>82</strong>
            <small>행정동 분석</small>
          </div>
          <div className="home-hero__visual-footer">
            <span><i className="status-dot" /> 최신 분석 반영</span>
            <span>상대 비교 기반</span>
          </div>
        </div>
      </section>

      <section className="summary-section" aria-labelledby="summary-heading">
        <div className="section-heading">
          <div>
            <p className="eyebrow">대전 상권 한눈에 보기</p>
            <h2 id="summary-heading">최근 분석 요약</h2>
          </div>
          <p className="period-label">
            {summary
              ? formatPeriodLabel(summary.period, summary.comparisonPeriods)
              : '최신 분석 완료 분기 기준'}
          </p>
        </div>

        {!summary && !error && <Loading message="최근 분석 요약을 불러오고 있습니다." />}
        {error && (
          <ErrorState
            message={error}
            onRetry={() => setLoadAttempt((attempt) => attempt + 1)}
          />
        )}
        {data && (
          <div className="summary-grid">
            <SummaryCard
              label="분석 행정동"
              value={data.analyzedDongCount}
              unit="개"
              description="대전 전체 행정동을 비교했습니다."
            />
            <SummaryCard
              label="중점검토 · 주의 지역"
              value={`${priorityCount} · ${cautionCount}`}
              unit="개"
              description="대전 내 상대적 검토 우선순위 기준입니다."
              tone="priority"
            />
            <SummaryCard
              label="대전 전체 점포 증감률"
              value={formatPercent(data.cityStoreGrowthRate)}
              unit={Number.isFinite(Number(data.cityStoreGrowthRate)) ? '%' : ''}
              description="직전 분기 대비 전체 점포 수 변화입니다."
              tone="growth"
            />
            <SummaryCard
              label="대전 체감 BSI"
              value={
                Number.isFinite(Number(data.latestBsi?.value))
                  ? Number(data.latestBsi.value).toFixed(1)
                  : '데이터 없음'
              }
              description={
                data.latestBsi
                  ? `${formatPeriod(data.latestBsi.periodMonth)} 기준 · 경기 맥락 참고 지표`
                  : '제공 가능한 최근 BSI가 없습니다.'
              }
              tone="bsi"
            />
          </div>
        )}
      </section>

      {data && (
        <section className="top-regions-section" aria-labelledby="top-regions-heading">
          <div className="section-heading">
            <div>
              <p className="eyebrow">먼저 살펴볼 지역</p>
              <h2 id="top-regions-heading">우선검토 지역 TOP 5</h2>
            </div>
            <Link className="text-link" to="/map">
              지도에서 전체 보기 →
            </Link>
          </div>
          <p className="section-description">
            행정동 종합 백분위를 기준으로 대전 내 상대적 검토 순서를
            보여줍니다.
          </p>
          <TopRegionList regions={data.topRegions} />
        </section>
      )}

      <div className="ai-prompt-wrapper">
        <AiPromptBox />
      </div>
    </main>
  )
}

export default HomePage
