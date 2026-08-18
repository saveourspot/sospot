function SummaryCard({ label, value, unit, description, tone = 'default' }) {
  return (
    <article className={`summary-card summary-card--${tone}`}>
      <p className="summary-card__label">{label}</p>
      <p className="summary-card__value">
        {value}
        {unit && <span className="summary-card__unit">{unit}</span>}
      </p>
      <p className="summary-card__description">{description}</p>
    </article>
  )
}

export default SummaryCard
