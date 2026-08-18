import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router-dom'
import Footer from './components/Footer.jsx'
import Header from './components/Header.jsx'
import Loading from './components/Loading.jsx'
import HomePage from './routes/HomePage.jsx'
import MapPage from './routes/MapPage.jsx'

const RegionDetailPage = lazy(() => import('./routes/RegionDetailPage.jsx'))

function App() {
  return (
    <div className="app-shell">
      <Header />
      <div className="app-content">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/map" element={<MapPage />} />
          <Route
            path="/regions/:dongCode"
            element={
              <Suspense
                fallback={
                  <main className="page-container">
                    <Loading message="상세 화면을 불러오고 있습니다." />
                  </main>
                }
              >
                <RegionDetailPage />
              </Suspense>
            }
          />
        </Routes>
      </div>
      <Footer />
    </div>
  )
}

export default App
