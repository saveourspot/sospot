export function formatPeriod(value) {
  if (typeof value !== 'string') return value ?? ''

  const compactPeriod = value.match(/^(\d{4})(\d{2})$/)
  if (compactPeriod) return `${compactPeriod[1]}.${compactPeriod[2]}`

  const dashedPeriod = value.match(/^(\d{4})-(\d{2})$/)
  if (dashedPeriod) return `${dashedPeriod[1]}.${dashedPeriod[2]}`

  return value
}
