function ErrorState({
  message = '데이터를 불러오지 못했습니다.',
  onRetry,
}) {
  return (
    <div className="state-panel state-panel--error" role="alert">
      <span className="state-panel__icon" aria-hidden="true">!</span>
      <p>{message}</p>
      <button type="button" className="retry-button" onClick={onRetry}>
        다시 시도
      </button>
    </div>
  )
}

export default ErrorState
