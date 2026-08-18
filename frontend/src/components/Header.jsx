import { NavLink } from 'react-router-dom'

function Header() {
  return (
    <header className="site-header">
      <div className="site-header__inner">
        <NavLink className="brand" to="/" aria-label="SOSpot 메인으로 이동">
          <span className="brand__mark" aria-hidden="true">SOS</span>
          <span>pot</span>
        </NavLink>
        <nav className="primary-nav" aria-label="주요 메뉴">
          <NavLink to="/" end>
            메인
          </NavLink>
          <NavLink to="/map">이상징후 지도</NavLink>
        </nav>
      </div>
    </header>
  )
}

export default Header
