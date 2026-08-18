import { useEffect, useState } from 'react'
import L from 'leaflet'
import { GeoJSON, MapContainer, TileLayer, useMap } from 'react-leaflet'
import ErrorState from '../components/ErrorState.jsx'
import Loading from '../components/Loading.jsx'

const DAEJEON_CENTER = [36.35, 127.38]
const EXPECTED_DONG_COUNT = 82
const GEOJSON_URL = `${import.meta.env.BASE_URL}geo/daejeon_dong.geojson`

const boundaryStyle = {
  color: '#176b52',
  weight: 1.2,
  opacity: 0.9,
  fillColor: '#cfe5dc',
  fillOpacity: 0.58,
}

function FitGeoJsonBounds({ geojson }) {
  const map = useMap()

  useEffect(() => {
    const bounds = L.geoJSON(geojson).getBounds()

    if (bounds.isValid()) {
      map.fitBounds(bounds, { padding: [20, 20] })
    }
  }, [geojson, map])

  return null
}

function MapPage() {
  const [geojson, setGeojson] = useState(null)
  const [error, setError] = useState(null)
  const [loadAttempt, setLoadAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    async function loadGeojson() {
      setError(null)

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
          setError(loadError)
        }
      }
    }

    loadGeojson()
    return () => controller.abort()
  }, [loadAttempt])

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

      <section className="map-panel" aria-label="대전 행정동 지도">
        {!geojson && !error && <Loading message="행정동 경계를 불러오고 있습니다." />}
        {error && (
          <ErrorState
            message={error.message}
            onRetry={() => setLoadAttempt((attempt) => attempt + 1)}
          />
        )}
        {geojson && (
          <MapContainer
            className="daejeon-map"
            center={DAEJEON_CENTER}
            zoom={11}
            minZoom={9}
            maxZoom={16}
            scrollWheelZoom
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <GeoJSON data={geojson} style={boundaryStyle} />
            <FitGeoJsonBounds geojson={geojson} />
          </MapContainer>
        )}
      </section>
    </main>
  )
}

export default MapPage
