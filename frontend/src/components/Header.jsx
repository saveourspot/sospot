import { NavLink } from 'react-router-dom'

function Header() {
  return (
    <header className="site-header">
      <div className="site-header__inner">
        <NavLink className="brand" to="/" aria-label="SOSpot 메인으로 이동">
          <span className="brand__symbol" aria-hidden="true">
            <svg viewBox="0 0 40 40" focusable="false">
              <path
                className="brand__pin"
                d="M20 4.5c-7.18 0-13 5.58-13 12.47 0 8.52 10.9 17.17 12.12 18.1a1.45 1.45 0 0 0 1.76 0C22.1 34.14 33 25.49 33 16.97 33 10.08 27.18 4.5 20 4.5Z"
              />
              <path className="brand__trend" d="m12.6 20.6 4.55-4.35 3.8 3.05 6.45-6.1" />
              <circle cx="12.6" cy="20.6" r="1.8" />
              <circle cx="17.15" cy="16.25" r="1.8" />
              <circle cx="20.95" cy="19.3" r="1.8" />
              <circle cx="27.4" cy="13.2" r="1.8" />
            </svg>
          </span>
          <span className="brand__wordmark">
            <span className="brand__name">
              SO<span className="brand__name-accent">Spot</span>
            </span>
            <small>대전 상권 변화 분석</small>
          </span>
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
