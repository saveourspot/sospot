import { Route, Routes } from 'react-router-dom'
import HomePage from './routes/HomePage.jsx'
import MapPage from './routes/MapPage.jsx'
import RegionDetailPage from './routes/RegionDetailPage.jsx'

function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/map" element={<MapPage />} />
      <Route path="/regions/:dongCode" element={<RegionDetailPage />} />
    </Routes>
  )
}

export default App
