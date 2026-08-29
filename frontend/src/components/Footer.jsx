import DataSourceBadge from './DataSourceBadge.jsx'

function Footer() {
  return (
    <footer className="site-footer">
      <div className="site-footer__inner">
        <DataSourceBadge />
        <div className="interpretation-note">
          <p>점포 수 감소가 개별 점포의 폐업을 의미하지 않습니다.</p>
          <p>등급은 대전 내 상대적인 검토 우선순위를 나타냅니다.</p>
        </div>
        <p className="site-footer__copyright">© SOSpot</p>
      </div>
    </footer>
  )
}

export default Footer
