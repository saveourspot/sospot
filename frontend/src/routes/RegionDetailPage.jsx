import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import AnomalyCategoryCard from '../components/AnomalyCategoryCard.jsx'
import Empty from '../components/Empty.jsx'
import ErrorState from '../components/ErrorState.jsx'
import Loading from '../components/Loading.jsx'
import RelativeGapChart from '../components/RelativeGapChart.jsx'
import RegionHeader from '../components/RegionHeader.jsx'
import TrendChart from '../components/TrendChart.jsx'
import { getBsi, getCommercialBenchmarks, getRegionDetail } from '../lib/api.js'
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

function formatPercent(value) {
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue)) return '데이터 없음'
  return `${numericValue > 0 ? '+' : ''}${(numericValue * 100).toFixed(1)}%`
}

function RegionDetailPage() {
  const { dongCode } = useParams()
  const [detailEnvelope, setDetailEnvelope] = useState(null)
  const [bsi, setBsi] = useState(null)
  const [commercialBenchmarks, setCommercialBenchmarks] = useState([])
  const [benchmarkLoading, setBenchmarkLoading] = useState(false)
  const [error, setError] = useState('')
  const [loadAttempt, setLoadAttempt] = useState(0)

  useEffect(() => {
    let ignore = false

    async function loadRegion() {
      setDetailEnvelope(null)
      setBsi(null)
      setCommercialBenchmarks([])
      setBenchmarkLoading(true)
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

      try {
        const benchmarkEnvelope = await getCommercialBenchmarks(
          dongCode,
          detailResult.value.period,
        )
        if (!ignore) {
          setCommercialBenchmarks(benchmarkEnvelope?.data?.benchmarkRegions ?? [])
        }
      } catch {
        if (!ignore) setCommercialBenchmarks([])
      } finally {
        if (!ignore) setBenchmarkLoading(false)
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
  const {
    header,
    topAnomalies = [],
    majorRelativeGaps = [],
    growthMomentum = [],
    excluded = [],
    trend,
  } = data
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

  const isPriorityGrade = header.grade === '중점검토' || header.grade === '주의'
  const isWatchGrade = header.grade === '관심'
  const isNormalGrade = header.grade === '정상'

  const summaryHeading = isPriorityGrade
    ? '왜 먼저 살펴봐야 하나요?'
    : isWatchGrade
      ? '어떤 신호를 보고 있나요?'
      : isNormalGrade
        ? '현재 어떤 상태인가요?'
        : '판정 결과 요약'

  const summaryBody = isNormalGrade ? (
    <p>
      <strong>{header.dongName}</strong>은 대전 {header.totalDongCount}개 행정동 중
      {' '}<strong>{header.rank}위</strong>로, 현재 뚜렷한 이상징후가 관측되지
      않았습니다. 대전 전체 흐름과 유사하거나 더 양호한 방향으로 움직이고
      있습니다.
    </p>
  ) : isWatchGrade ? (
    <p>
      <strong>{header.dongName}</strong>은 대전 {header.totalDongCount}개 행정동 중
      {' '}<strong>{header.rank}위</strong>이며, 이상징후 업종은
      {' '}<strong>{header.anomalyCatCount}개</strong>로 확인됩니다. 우선 검토
      대상은 아니지만 흐름을 지켜볼 만합니다.
      {strongestAnomaly && (
        <> 가장 눈여겨볼 업종은 <strong>{strongestAnomaly.catName}</strong>으로,
          대전 전체 동일 업종 대비 상대격차는
          {' '}<strong>{formatPercentPoint(strongestAnomaly.relativeGap)}</strong>입니다.</>
      )}
    </p>
  ) : (
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
  )

  return (
    <main className="page-container region-detail-page">
      <RegionHeader
        header={header}
        periodLabel={formatPeriodLabel(period, comparisonPeriods)}
      />

      <section className="reason-summary" aria-labelledby="reason-summary-heading">
        <div>
          <p className="eyebrow">판정 근거</p>
          <h2 id="reason-summary-heading">{summaryHeading}</h2>
        </div>
        {summaryBody}
        <span>
          이 지표는 미래를 예측하거나 정책지원 대상을 자동 결정하지 않습니다.
        </span>
      </section>

      <section className="positive-changes detail-section detail-section--support" aria-labelledby="positive-changes-heading">
        <div className="section-heading">
          <div>
            <p className="eyebrow">함께 확인할 변화</p>
            <h2 id="positive-changes-heading">성장 모멘텀과 정책 검토 방향</h2>
          </div>
          <p className="period-label">최대 3개 업종</p>
        </div>
        <p className="section-description">
          최근 증가 또는 대전 대비 상대적으로 양호한 업종을 유형화하고, 다음
          단계에서 확인할 수 있는 정책 검토 항목을 제시합니다.
        </p>
        {growthMomentum.length > 0 ? (
          <div className="positive-change-grid">
            {growthMomentum.map((momentum) => (
              <article key={momentum.catCode} className="positive-change-card">
                <div className="positive-change-card__heading">
                  <strong>{momentum.catName}</strong>
                  <span>{momentum.momentumType}</span>
                </div>
                <p className="positive-change-card__counts">
                  {(momentum.storeCounts ?? []).map((point) => point.count).join(' → ')}개
                </p>
                <dl>
                  <div>
                    <dt>최근 지역 증감률</dt>
                    <dd>{formatPercent(momentum.growthRate)}</dd>
                  </div>
                  <div>
                    <dt>대전 대비 상대격차</dt>
                    <dd>{formatPercentPoint(momentum.relativeGap)}</dd>
                  </div>
                </dl>
                <h3>정책 검토 체크리스트</h3>
                <ul>
                  {(momentum.reviewDirections ?? []).map((direction) => (
                    <li key={direction}>{direction}</li>
                  ))}
                </ul>
              </article>
            ))}
          </div>
        ) : (
          <Empty message="현재 기준에서 표시할 성장 모멘텀 업종이 없습니다." />
        )}
        <p className="positive-changes__notice detail-notice">
          점포 수 변화만으로 성장 원인이나 정책 효과를 단정할 수 없어 현장 자료
          확인이 필요합니다.
        </p>
      </section>

      <section className="relative-gap-section detail-section detail-section--core" aria-labelledby="relative-gap-heading">
        <div className="section-heading">
          <div>
            <p className="eyebrow">대전 전체 대비</p>
            <h2 id="relative-gap-heading">업종별 상대격차</h2>
          </div>
          <p className="period-label">유효 대분류 {validRelativeGaps.length}개</p>
        </div>
        <p className="section-description">
          음수는 해당 지역의 점포 수 변화가 대전 전체 동일 업종보다 상대적으로
          낮았음을 의미합니다. 상대격차는 두 증감률의 차이이므로 퍼센트가 아닌
          퍼센트포인트(%p)로 표시합니다.
        </p>
        <RelativeGapChart categories={validRelativeGaps} />
      </section>

      <section className="top-anomalies detail-section detail-section--core" aria-labelledby="top-anomalies-heading">
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

      <section className="trend-section detail-section detail-section--core" aria-labelledby="trend-heading">
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
        <p className="trend-section__notice detail-notice">
          BSI는 이상징후 점수와 등급 계산에 사용되지 않으며, 지역×업종 교차
          BSI를 의미하지 않습니다.
        </p>
      </section>

      <section className="commercial-benchmarks detail-section detail-section--support" aria-labelledby="commercial-benchmarks-heading">
        <div className="section-heading">
          <div>
            <p className="eyebrow">유사 상권 벤치마킹</p>
            <h2 id="commercial-benchmarks-heading">비슷한 상권 중 상대적으로 양호한 지역</h2>
          </div>
          <p className="period-label">대분류 업종 구성 기준</p>
        </div>
        <p className="section-description">
          업종별 점포 구성 비율이 비슷한 행정동을 찾고, 그중 상대격차가 더 높은
          업종과 선택 지역에 접목하기 전에 확인할 사항을 보여줍니다.
        </p>
        {benchmarkLoading ? (
          <Loading message="상권 유형이 비슷한 지역의 업종 흐름을 비교하고 있습니다." />
        ) : commercialBenchmarks.length > 0 ? (
          <div className="commercial-benchmark-grid">
            {commercialBenchmarks.map((benchmark) => (
              <article key={benchmark.dongCode} className="commercial-benchmark-card">
                <div className="commercial-benchmark-card__heading">
                  <div>
                    <span>{benchmark.sigungu}</span>
                    <h3>{benchmark.dongName}</h3>
                  </div>
                  <p>상권 구성 유사도 {benchmark.commercialMixSimilarity}%</p>
                </div>
                <p className="commercial-benchmark-card__summary">
                  {header.dongName}보다 상대격차가 높은 유효 업종{' '}
                  <strong>{benchmark.advantageCategoryCount}개</strong>
                </p>
                <div className="benchmark-category-list">
                  {benchmark.advantageCategories.map((category) => (
                    <div key={category.catCode} className="benchmark-category">
                      <div className="benchmark-category__title">
                        <strong>{category.catName}</strong>
                        <span>
                          격차 {formatPercentPoint(category.relativeGapDifference)}
                        </span>
                      </div>
                      <div className="benchmark-category__comparison">
                        <div>
                          <span>{header.dongName}</span>
                          <strong>{formatPercentPoint(category.targetRelativeGap)}</strong>
                          <small>
                            {(category.targetStoreCounts ?? []).map((point) => point.count).join(' → ')}개
                          </small>
                        </div>
                        <div>
                          <span>{benchmark.dongName}</span>
                          <strong>{formatPercentPoint(category.benchmarkRelativeGap)}</strong>
                          <small>
                            {(category.benchmarkStoreCounts ?? []).map((point) => point.count).join(' → ')}개
                          </small>
                        </div>
                      </div>
                      <h4>접목 전 확인할 사항</h4>
                      <ul>
                        {category.applicationDirections.map((direction) => (
                          <li key={direction}>{direction}</li>
                        ))}
                      </ul>
                    </div>
                  ))}
                </div>
              </article>
            ))}
          </div>
        ) : (
          <Empty message="현재 기준에서 비교 가능한 유사 상권 우위 지역이 없습니다." />
        )}
        <p className="commercial-benchmarks__notice detail-notice">
          유사도는 최신 분기의 대분류 업종별 점포 구성으로 계산합니다. 상대격차가
          높다는 사실만으로 성장 원인이나 정책 효과를 단정할 수 없으며, 우위 지역의
          사례를 그대로 복제하지 말고 입지·고객층·주변 업종 차이를 먼저 확인해야 합니다.
        </p>
      </section>

      <section className="excluded-categories detail-section detail-section--reference" aria-labelledby="excluded-heading">
        <div className="section-heading">
          <div>
            <p className="eyebrow">판정 제외</p>
            <h2 id="excluded-heading">표본 부족 업종</h2>
          </div>
          <p className="period-label">{excluded.length}개 업종</p>
        </div>
        <p className="section-description">
          비교 시작 분기({formatPeriod(comparisonPeriods[0])})의 점포 수가 20개
          미만인 업종은 이상징후 점수와 등급을 계산하지 않습니다.
        </p>
        {excluded.length > 0 ? (
          <ul className="excluded-categories__list">
            {excluded.map((category) => (
              <li key={category.catCode}>
                <strong>{category.catName}</strong>
                <span>{category.storeCount}개</span>
              </li>
            ))}
          </ul>
        ) : (
          <Empty message="표본 부족으로 판정에서 제외된 대분류 업종이 없습니다." />
        )}
      </section>

    </main>
  )
}

export default RegionDetailPage
