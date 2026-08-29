import { DAEJEON_SIGUNGU } from '../lib/mapFilters.js'

function SigunguFilter({ selected, onChange }) {
  const isAllSelected = selected.length === DAEJEON_SIGUNGU.length

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
        <label className="sigungu-filter__all">
          <input
            type="checkbox"
            checked={isAllSelected}
            onChange={() => onChange(isAllSelected ? [] : DAEJEON_SIGUNGU)}
          />
          <span>전체 자치구</span>
        </label>
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
