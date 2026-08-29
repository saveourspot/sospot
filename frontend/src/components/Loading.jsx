function Loading({ message = '데이터를 불러오고 있습니다.' }) {
  return (
    <div className="state-panel state-panel--loading" role="status" aria-live="polite">
      <span className="loading-spinner" aria-hidden="true" />
      <p>{message}</p>
    </div>
  )
}

export default Loading
