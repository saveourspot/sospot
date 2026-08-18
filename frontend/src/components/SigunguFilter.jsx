import { DAEJEON_SIGUNGU } from '../lib/mapFilters.js'

function SigunguFilter({ selected, onChange }) {
  const toggleSigungu = (sigungu) => {
    const nextSelected = selected.includes(sigungu)
      ? selected.filter((item) => item !== sigungu)
      : [...selected, sigungu]

    onChange(nextSelected)
  }

  return (
    <fieldset className="sigungu-filter">
      <legend>자치구</legend>
      <div>
        {DAEJEON_SIGUNGU.map((sigungu) => (
          <label key={sigungu}>
            <input
              type="checkbox"
              checked={selected.includes(sigungu)}
              onChange={() => toggleSigungu(sigungu)}
            />
            <span>{sigungu}</span>
          </label>
        ))}
      </div>
    </fieldset>
  )
}

export default SigunguFilter
