function Empty({ message = '표시할 데이터가 없습니다.' }) {
  return (
    <div className="state-panel" role="status">
      <span className="state-panel__icon" aria-hidden="true">−</span>
      <p>{message}</p>
    </div>
  )
}

export default Empty
