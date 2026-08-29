import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import L from 'leaflet'
import { GeoJSON, MapContainer, TileLayer, useMap } from 'react-leaflet'
import { useNavigate } from 'react-router-dom'
import CategoryFilter from '../components/CategoryFilter.jsx'
import ErrorState from '../components/ErrorState.jsx'
import Loading from '../components/Loading.jsx'
import MapLegend from '../components/MapLegend.jsx'
import RegionList from '../components/RegionList.jsx'
import SigunguFilter from '../components/SigunguFilter.jsx'
import { getSelectedCategoryScores } from '../lib/api.js'
import { GRADE_COLORS, NO_DATA_COLOR } from '../lib/gradeStyles.js'
import { DAEJEON_SIGUNGU, MAJOR_CATEGORIES } from '../lib/mapFilters.js'
import { formatPeriod } from '../lib/periodFormat.js'

const DAEJEON_CENTER = [36.35, 127.38]
const EXPECTED_DONG_COUNT = 82
const GEOJSON_URL = `${import.meta.env.BASE_URL}geo/daejeon_dong.geojson`

function FitGeoJsonBounds({ geojson }) {
  const map = useMap()

  useEffect(() => {
    const bounds = L.geoJSON(geojson).getBounds()

    if (bounds.isValid()) {
      const daejeonBounds = bounds.pad(0.05)
      map.setMaxBounds(daejeonBounds)
      map.fitBounds(bounds, { padding: [20, 20] })
      map.setMinZoom(map.getZoom())
    }
  }, [geojson, map])

  return null
}

function createTooltipContent(feature, item, isCategorySelected) {
  const container = document.createElement('div')
  container.className = 'region-tooltip'

  const heading = document.createElement('strong')
  heading.textContent = feature.properties.dong_name

  const sigungu = document.createElement('span')
  sigungu.className = 'region-tooltip__sigungu'
  sigungu.textContent = feature.properties.sggnm

  const details = document.createElement('dl')
  const gradeLabel = document.createElement('dt')
  const gradeValue = document.createElement('dd')
  const anomalyLabel = document.createElement('dt')
  const anomalyValue = document.createElement('dd')

  gradeLabel.textContent = '등급'
  gradeValue.textContent = item?.grade ?? '데이터 없음'
  anomalyLabel.textContent = isCategorySelected ? '우선순위 반영 업종' : '이상 업종 수'
  anomalyValue.textContent = isCategorySelected
    ? item?.validCategoryCount > 0
      ? `${item.validCategoryCount}/${item.selectedCategoryCount}개`
      : '표본 부족으로 판정 제외'
    : Number.isInteger(item?.anomalyCatCount) ? `${item.anomalyCatCount}개` : '데이터 없음'

  details.append(gradeLabel, gradeValue, anomalyLabel, anomalyValue)
  container.append(heading, sigungu, details)
  return container
}

function MapPage() {
  const navigate = useNavigate()
  const [geojson, setGeojson] = useState(null)
  const [regionScores, setRegionScores] = useState([])
  const [selectedCategories, setSelectedCategories] = useState(() =>
    MAJOR_CATEGORIES.map((category) => category.code),
  )
  const [selectedSigungu, setSelectedSigungu] = useState(DAEJEON_SIGUNGU)
  const [highlightedDongCode, setHighlightedDongCode] = useState(null)
  const [analysisPeriod, setAnalysisPeriod] = useState('')
  const [geoError, setGeoError] = useState(null)
  const [scoreError, setScoreError] = useState(null)
  const [isScoresLoading, setIsScoresLoading] = useState(true)
  const [loadAttempt, setLoadAttempt] = useState(0)
  const layerByDongCode = useRef(new Map())

  useEffect(() => {
    const controller = new AbortController()

    async function loadGeojson() {
      setGeoError(null)

      try {
        const response = await fetch(GEOJSON_URL, { signal: controller.signal })

        if (!response.ok) {
          throw new Error(`GeoJSON 요청 실패: ${response.status}`)
        }

        const data = await response.json()

        if (
          data.type !== 'FeatureCollection' ||
          !Array.isArray(data.features) ||
          data.features.length !== EXPECTED_DONG_COUNT
        ) {
          throw new Error('대전 행정동 82개 경계를 확인할 수 없습니다.')
        }

        setGeojson(data)
      } catch (loadError) {
        if (loadError.name !== 'AbortError') {
          setGeoError(loadError)
        }
      }
    }

    loadGeojson()
    return () => controller.abort()
  }, [loadAttempt])

  useEffect(() => {
    let ignore = false

    async function loadRegionScores() {
      setRegionScores([])
      setScoreError(null)

      if (selectedCategories.length === 0) {
        setAnalysisPeriod('')
        setIsScoresLoading(false)
        return
      }

      setIsScoresLoading(true)

      try {
        const result = await getSelectedCategoryScores(selectedCategories)
        const items = result?.data?.items

        if (!Array.isArray(items)) {
          throw new Error('지역 점수 응답 형식을 확인할 수 없습니다.')
        }

        if (!ignore) {
          setAnalysisPeriod(result.period)
          setRegionScores(items)
        }
      } catch (requestError) {
        if (!ignore) {
          setScoreError(
            requestError.response?.data?.message ||
              requestError.message ||
              '지역 분석 결과를 불러오지 못했습니다.',
          )
        }
      } finally {
        if (!ignore) setIsScoresLoading(false)
      }
    }

    loadRegionScores()
    return () => {
      ignore = true
    }
  }, [selectedCategories, loadAttempt])

  const gradeByDongCode = useMemo(
    () => new Map(regionScores.map((item) => [item.dongCode, item])),
    [regionScores],
  )

  const getBoundaryStyle = useCallback(
    (feature) => {
      const item = gradeByDongCode.get(feature.properties.dong_code)
      const isSelectedSigungu = selectedSigungu.includes(feature.properties.sggnm)
      const hasSelectedCategory = selectedCategories.length > 0
      const hasUsableGrade =
        item?.sampleSizeFlag !== 'LOW' && Boolean(GRADE_COLORS[item?.grade])

      return {
        color: hasSelectedCategory ? '#ffffff' : '#8fa2c1',
        weight: 1.1,
        opacity: hasSelectedCategory ? isSelectedSigungu ? 0.95 : 0.25 : 0.72,
        fillColor: hasSelectedCategory
          ? hasUsableGrade ? GRADE_COLORS[item.grade] : NO_DATA_COLOR
          : '#dce5f3',
        fillOpacity: hasSelectedCategory ? isSelectedSigungu ? 0.8 : 0.08 : 0.2,
      }
    },
    [gradeByDongCode, selectedCategories.length, selectedSigungu],
  )

  const onEachFeature = (feature, layer) => {
    const dongCode = feature.properties.dong_code
    layerByDongCode.current.set(dongCode, layer)
    layer.bindTooltip(createTooltipContent(
      feature,
      gradeByDongCode.get(dongCode),
      selectedCategories.length > 0,
    ), {
      className: 'region-map-tooltip',
      direction: 'top',
      interactive: false,
      opacity: 1,
      permanent: false,
      sticky: true,
    })
    layer.on({
      tooltipopen: () => {
        layerByDongCode.current.forEach((otherLayer) => {
          if (otherLayer !== layer && otherLayer.isTooltipOpen()) {
            otherLayer.closeTooltip()
          }
        })
      },
      mouseover: () => setHighlightedDongCode(dongCode),
      mouseout: () => {
        layer.closeTooltip()
        setHighlightedDongCode(null)
      },
      click: () => navigate(`/regions/${dongCode}`),
    })
  }

  useEffect(() => {
    layerByDongCode.current.forEach((layer, dongCode) => {
      const baseStyle = getBoundaryStyle(layer.feature)
      const isHighlighted = highlightedDongCode === dongCode
      layer.setTooltipContent(
        createTooltipContent(
          layer.feature,
          gradeByDongCode.get(dongCode),
          selectedCategories.length > 0,
        ),
      )

      layer.setStyle(
        isHighlighted
          ? {
              ...baseStyle,
              color: '#173d31',
              weight: 3.2,
              opacity: 1,
              fillOpacity: 0.95,
            }
          : baseStyle,
      )

      if (isHighlighted) {
        layer.bringToFront()
      }
    })
  }, [getBoundaryStyle, gradeByDongCode, highlightedDongCode, selectedCategories])

  const selectedCategoryName = selectedCategories.length === 0
    ? '업종 미선택'
    : selectedCategories.length === MAJOR_CATEGORIES.length
      ? '전체 업종'
    : MAJOR_CATEGORIES.filter((category) => selectedCategories.includes(category.code))
        .map((category) => category.name)
        .join(', ')
  const visibleRegions = regionScores.filter((region) =>
    selectedSigungu.includes(region.sigungu),
  )

  return (
    <main className="page-container map-page">
      <div className="map-page__heading">
        <div>
          <p className="eyebrow">대전 82개 행정동</p>
          <h1>이상징후 지도</h1>
          <p>
            행정동 경계를 기준으로 최근 상대적 이상징후를 확인합니다.
          </p>
        </div>
        {geojson && (
          <span className="map-page__count">경계 {geojson.features.length}개 로드</span>
        )}
      </div>

      <section className="map-filters" aria-label="지도 필터">
        <div className="map-filters__heading">
          <div>
            <span>ANALYSIS FILTER</span>
            <h2>분석 조건</h2>
          </div>
          <p className="map-filters__status" aria-live="polite">
            {selectedCategoryName} · 자치구 {selectedSigungu.length}개 선택
          </p>
        </div>
        <CategoryFilter selected={selectedCategories} onChange={setSelectedCategories} />
        <SigunguFilter selected={selectedSigungu} onChange={setSelectedSigungu} />
      </section>

      <div className="map-workspace">
        <section className="map-panel" aria-label="대전 행정동 지도">
          {!geojson && !geoError && <Loading message="행정동 경계를 불러오고 있습니다." />}
          {geoError && (
            <ErrorState
              message={geoError.message}
              onRetry={() => setLoadAttempt((attempt) => attempt + 1)}
            />
          )}
          {geojson && scoreError && (
            <ErrorState
              message={scoreError}
              onRetry={() => setLoadAttempt((attempt) => attempt + 1)}
            />
          )}
          {geojson && !scoreError && (
            <>
              <div className="map-preview-badge">
                {selectedCategories.length === 0
                  ? '업종을 선택해 주세요'
                  : isScoresLoading
                  ? '분석 결과 불러오는 중'
                  : `${formatPeriod(analysisPeriod)} 분석 결과`}
              </div>
              {selectedCategories.length > 0 && <MapLegend />}
              <MapContainer
                className="daejeon-map"
                center={DAEJEON_CENTER}
                zoom={11}
                minZoom={9}
                maxZoom={16}
                maxBoundsViscosity={1}
                scrollWheelZoom
              >
                <TileLayer
                  attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                  url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                />
                <GeoJSON
                  key={`${selectedCategories.join('-')}-${selectedSigungu.join('-')}`}
                  data={geojson}
                  style={getBoundaryStyle}
                  onEachFeature={onEachFeature}
                />
                <FitGeoJsonBounds geojson={geojson} />
              </MapContainer>
            </>
          )}
        </section>
        <RegionList
          regions={visibleRegions}
          highlightedDongCode={highlightedDongCode}
          onHighlight={setHighlightedDongCode}
          emptyMessage={
            selectedCategories.length === 0
              ? '지도에 표시할 업종을 선택해 주세요.'
              : undefined
          }
        />
      </div>
    </main>
  )
}

export default MapPage
