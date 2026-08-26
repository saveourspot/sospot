import { MAJOR_CATEGORIES } from '../lib/mapFilters.js'

function CategoryFilter({ selected, onChange }) {
  const isAllSelected = selected.length === MAJOR_CATEGORIES.length

  const toggleCategory = (code) => {
    onChange(
      selected.includes(code)
        ? selected.filter((selectedCode) => selectedCode !== code)
        : [...selected, code],
    )
  }

  return (
    <fieldset className="category-filter" aria-label="업종">
      <legend>업종</legend>
      <div>
        <label className="category-filter__all">
          <input
            type="checkbox"
            checked={isAllSelected}
            onChange={() =>
              onChange(isAllSelected ? [] : MAJOR_CATEGORIES.map((category) => category.code))
            }
          />
          <span>전체 업종</span>
        </label>
        {MAJOR_CATEGORIES.map((category) => (
          <label key={category.code}>
            <input
              type="checkbox"
              checked={selected.includes(category.code)}
              onChange={() => toggleCategory(category.code)}
            />
            <span>{category.name}</span>
          </label>
        ))}
      </div>
      <p>선택 업종 중 표본 기준을 충족한 업종으로 82개 행정동의 검토 우선순위를 다시 산정합니다.</p>
    </fieldset>
  )
}

export default CategoryFilter
