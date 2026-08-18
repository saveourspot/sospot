const GRADE_STYLES = {
  정상: 'normal',
  관심: 'interest',
  주의: 'caution',
  중점검토: 'priority',
}

function GradeBadge({ grade }) {
  const style = GRADE_STYLES[grade]

  if (!style) {
    return null
  }

  return <span className={`grade-badge grade-badge--${style}`}>{grade}</span>
}

export default GradeBadge
