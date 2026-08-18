import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import AnomalyCategoryCard from '../components/AnomalyCategoryCard.jsx'
import Empty from '../components/Empty.jsx'
import ErrorState from '../components/ErrorState.jsx'
import Loading from '../components/Loading.jsx'
import RegionHeader from '../components/RegionHeader.jsx'

const GEOJSON_URL = `${import.meta.env.BASE_URL}geo/daejeon_dong.geojson`

function RegionDetailPage() {
  const { dongCode } = useParams()
  const [header, setHeader] = useState(null)
  const [notFound, setNotFound] = useState(false)
  const [error, setError] = useState(null)
  const [loadAttempt, setLoadAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    async function loadRegion() {
      setHeader(null)
      setNotFound(false)
      setError(null)

      try {
        const response = await fetch(GEOJSON_URL, { signal: controller.signal })

        if (!response.ok) {
          throw new Error(`행정동 정보 요청 실패: ${response.status}`)
        }

        const geojson = await response.json()
        const feature = geojson.features?.find(
          (item) => item.properties?.dong_code === dongCode,
        )

        if (!feature) {
          setNotFound(true)
          return
        }

        setHeader({
          dongCode,
          dongName: feature.properties.dong_name,
          sigungu: feature.properties.sggnm,
          grade: null,
          pctScore: null,
          rank: null,
          totalDongCount: null,
        })
      } catch (loadError) {
        if (loadError.name !== 'AbortError') {
          setError(loadError)
        }
      }
    }

    loadRegion()
    return () => controller.abort()
  }, [dongCode, loadAttempt])

  if (!header && !notFound && !error) {
    return (
      <main className="page-container">
        <Loading message="행정동 정보를 불러오고 있습니다." />
      </main>
    )
  }

  if (error) {
    return (
      <main className="page-container">
        <ErrorState
          message={error.message}
          onRetry={() => setLoadAttempt((attempt) => attempt + 1)}
        />
      </main>
    )
  }

  if (notFound) {
    return (
      <main className="page-container">
        <Empty message="지원하는 대전 행정동을 찾을 수 없습니다." />
      </main>
    )
  }

  return (
    <main className="page-container region-detail-page">
      <RegionHeader
        header={header}
        periodLabel="최신 분석 완료 분기 · 상세 API 연동 예정"
      />

      <section className="reason-summary" aria-labelledby="reason-summary-heading">
        <div>
          <p className="eyebrow">판정 근거</p>
          <h2 id="reason-summary-heading">왜 먼저 살펴봐야 하나요?</h2>
        </div>
        <p>
          <strong>{header.dongName}</strong>의 순위, 이상 업종 수, 주요 업종과
          최대 상대격차는 상세 분석 API 연결 후 실제 저장 결과를 바탕으로
          표시됩니다.
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
          <p className="period-label">상세 분석 API 연동 예정</p>
        </div>
        <p className="section-description">
          업종별 점포 수 변화와 대전 전체 동일 업종 흐름의 상대격차를 함께
          확인합니다.
        </p>
        <div className="anomaly-card-grid">
          {[1, 2, 3].map((rank) => (
            <AnomalyCategoryCard key={rank} rank={rank} anomaly={null} />
          ))}
        </div>
      </section>
    </main>
  )
}

export default RegionDetailPage
