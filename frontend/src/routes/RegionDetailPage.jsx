import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import AnomalyCategoryCard from '../components/AnomalyCategoryCard.jsx'
import Empty from '../components/Empty.jsx'
import ErrorState from '../components/ErrorState.jsx'
import Loading from '../components/Loading.jsx'
import RelativeGapChart from '../components/RelativeGapChart.jsx'
import RegionHeader from '../components/RegionHeader.jsx'
import TrendChart from '../components/TrendChart.jsx'
import { getBsi, getRegionDetail } from '../lib/api.js'
import { formatPeriod } from '../lib/periodFormat.js'

function formatPeriodLabel(period, comparisonPeriods = []) {
  if (!period) return '분석 기준 시점 없음'

  const periods = comparisonPeriods
    .map(formatPeriod)
    .join(' → ')
  return `${formatPeriod(period)} 기준 · ${periods}`
}

function formatPercentPoint(value) {
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue)) return '데이터 없음'
  return `${numericValue > 0 ? '+' : ''}${(numericValue * 100).toFixed(1)}%p`
}

function RegionDetailPage() {
  const { dongCode } = useParams()
  const [detailEnvelope, setDetailEnvelope] = useState(null)
  const [bsi, setBsi] = useState(null)
  const [error, setError] = useState('')
  const [loadAttempt, setLoadAttempt] = useState(0)

  useEffect(() => {
    let ignore = false

    async function loadRegion() {
      setDetailEnvelope(null)
      setBsi(null)
      setError('')

      const [detailResult, bsiResult] = await Promise.allSettled([
        getRegionDetail(dongCode),
        getBsi(),
      ])

      if (ignore) return

      if (detailResult.status === 'rejected') {
        const requestError = detailResult.reason
        setError(
          requestError.response?.data?.message ||
            requestError.message ||
            '행정동 상세 분석 결과를 불러오지 못했습니다.',
        )
        return
      }

      if (!detailResult.value?.data?.header) {
        setError('행정동 상세 응답 형식을 확인할 수 없습니다.')
        return
      }

      setDetailEnvelope(detailResult.value)
      if (bsiResult.status === 'fulfilled') {
        setBsi(bsiResult.value?.data ?? null)
      }
    }

    loadRegion()
    return () => {
      ignore = true
    }
  }, [dongCode, loadAttempt])

  if (!detailEnvelope && !error) {
    return (
      <main className="page-container">
        <Loading message="행정동 상세 분석 결과를 불러오고 있습니다." />
      </main>
    )
  }

  if (error) {
    return (
      <main className="page-container">
        <ErrorState
          message={error}
          onRetry={() => setLoadAttempt((attempt) => attempt + 1)}
        />
      </main>
    )
  }

  const { period, comparisonPeriods, data } = detailEnvelope
  const { header, topAnomalies = [], majorRelativeGaps = [], excluded = [], trend } = data
  const validRelativeGaps = majorRelativeGaps
    .filter(
      (category) =>
        category.sampleSizeFlag === 'OK' &&
        Number.isFinite(Number(category.relativeGap)),
    )
    .map((category) => ({
      ...category,
      relativeGap: Number(category.relativeGap) * 100,
    }))
  const bsiByPeriod = new Map(
    (bsi?.quarterlySeries ?? []).map((point) => [point.period, point.value]),
  )
  const trendSeries = (trend?.series ?? []).map((point) => ({
    ...point,
    bsi: bsiByPeriod.get(point.period) ?? null,
  }))
  const strongestAnomaly = topAnomalies[0]

  return (
    <main className="page-container region-detail-page">
      <RegionHeader
        header={header}
        periodLabel={formatPeriodLabel(period, comparisonPeriods)}
      />

      <section className="reason-summary" aria-labelledby="reason-summary-heading">
        <div>
          <p className="eyebrow">판정 근거</p>
          <h2 id="reason-summary-heading">왜 먼저 살펴봐야 하나요?</h2>
        </div>
        <p>
          <strong>{header.dongName}</strong>은 대전 {header.totalDongCount}개 행정동 중
          {' '}<strong>{header.rank}위</strong>이며, 이상징후 업종은
          {' '}<strong>{header.anomalyCatCount}개</strong>입니다.
          {strongestAnomaly && (
            <> 가장 높은 업종은 <strong>{strongestAnomaly.catName}</strong>으로,
              대전 전체 동일 업종 대비 상대격차는
              {' '}<strong>{formatPercentPoint(strongestAnomaly.relativeGap)}</strong>입니다.</>
          )}
        </p>
        <span>
          이 지표는 미래를 예측하거나 정책지원 대상을 자동 결정하지 않습니다.
        </span>
      </section>

      <section className="top-anomalies" aria-labelledby="top-anomalies-heading">
        <div className="section-heading">
          <div>
            <p className="eyebrow">업종별 판정 근거</p>
            <h2 id="top-anomalies-heading">이상 업종 TOP 3</h2>
          </div>
          <p className="period-label">{formatPeriod(period)} 분석 결과</p>
        </div>
        <p className="section-description">
          업종별 점포 수 변화와 대전 전체 동일 업종 흐름의 상대격차를 함께
          확인합니다.
        </p>
        {topAnomalies.length > 0 ? (
          <div className="anomaly-card-grid">
            {topAnomalies.map((anomaly, index) => (
              <AnomalyCategoryCard
                key={anomaly.catCode}
                rank={index + 1}
                anomaly={anomaly}
              />
            ))}
          </div>
        ) : (
          <Empty message="표본 기준을 충족한 업종 분석 결과가 없습니다." />
        )}
      </section>

      <section className="trend-section" aria-labelledby="trend-heading">
        <div className="section-heading">
          <div>
            <p className="eyebrow">최근 변화 비교</p>
            <h2 id="trend-heading">지역과 대전 전체 추세</h2>
          </div>
          <p className="period-label">
            {trend ? `${trend.catName} · ${trendSeries.length}개 분석 분기` : '추세 없음'}
          </p>
        </div>
        <p className="section-description">
          첫 분기를 100으로 환산해 해당 지역과 대전 전체 동일 업종의 변화 방향을
          비교하고, 대전 체감 BSI는 보조적인 경기 맥락으로만 제공합니다.
        </p>
        <TrendChart
          series={trendSeries}
          bsiPeriodLabel={formatPeriod(bsi?.periodMonth)}
        />
        <p className="trend-section__notice">
          BSI는 이상징후 Score와 등급 계산에 사용되지 않으며, 지역×업종 교차
          BSI를 의미하지 않습니다.
        </p>
      </section>

      <section className="relative-gap-section" aria-labelledby="relative-gap-heading">
        <div className="section-heading">
          <div>
            <p className="eyebrow">대전 전체 대비</p>
            <h2 id="relative-gap-heading">업종별 상대격차</h2>
          </div>
          <p className="period-label">유효 대분류 {validRelativeGaps.length}개</p>
        </div>
        <p className="section-description">
          음수는 해당 지역의 점포 수 변화가 대전 전체 동일 업종보다 상대적으로
          낮았음을 의미합니다.
        </p>
        <RelativeGapChart categories={validRelativeGaps} />
      </section>

      <section className="excluded-categories" aria-labelledby="excluded-heading">
        <div className="section-heading">
          <div>
            <p className="eyebrow">판정 제외</p>
            <h2 id="excluded-heading">표본 부족 업종</h2>
          </div>
          <p className="period-label">{excluded.length}개 업종</p>
        </div>
        <p className="section-description">
          기준 분기보다 두 분기 전 점포 수가 20개 미만인 업종은 이상징후
          점수와 등급을 계산하지 않습니다.
        </p>
        {excluded.length > 0 ? (
          <ul className="excluded-categories__list">
            {excluded.map((category) => (
              <li key={category.catCode}>
                <strong>{category.catName}</strong>
                <span>{category.reason}</span>
              </li>
            ))}
          </ul>
        ) : (
          <Empty message="표본 부족으로 판정에서 제외된 대분류 업종이 없습니다." />
        )}
      </section>

      <section className="later-features" aria-labelledby="later-features-heading">
        <div>
          <p className="eyebrow">후순위 기능</p>
          <h2 id="later-features-heading">추가 분석 화면</h2>
          <p>
            핵심 MVP 완성 후 업종 분석과 지역 비교 전용 화면을 순차적으로
            준비합니다.
          </p>
        </div>
        <div className="later-features__items">
          <div aria-disabled="true">
            <strong>업종 분석</strong>
            <span>F3 · 준비 중</span>
          </div>
          <div aria-disabled="true">
            <strong>지역 비교</strong>
            <span>F4 · 준비 중</span>
          </div>
        </div>
      </section>
    </main>
  )
}

export default RegionDetailPage
