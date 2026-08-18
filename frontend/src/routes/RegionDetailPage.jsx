import { useParams } from 'react-router-dom'

function RegionDetailPage() {
  const { dongCode } = useParams()

  return (
    <main className="page-container">
      <h1>행정동 상세</h1>
      <p>행정동 코드: {dongCode}</p>
    </main>
  )
}

export default RegionDetailPage
