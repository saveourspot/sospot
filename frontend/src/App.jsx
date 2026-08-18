import { Route, Routes } from 'react-router-dom'
import Footer from './components/Footer.jsx'
import Header from './components/Header.jsx'
import HomePage from './routes/HomePage.jsx'
import MapPage from './routes/MapPage.jsx'
import RegionDetailPage from './routes/RegionDetailPage.jsx'

function App() {
  return (
    <div className="app-shell">
      <Header />
      <div className="app-content">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/map" element={<MapPage />} />
          <Route path="/regions/:dongCode" element={<RegionDetailPage />} />
        </Routes>
      </div>
      <Footer />
    </div>
  )
}

export default App
