import { MAJOR_CATEGORIES } from '../lib/mapFilters.js'

function CategoryFilter({ value, onChange }) {
  return (
    <label className="filter-field">
      <span>업종</span>
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">전체 업종</option>
        {MAJOR_CATEGORIES.map((category) => (
          <option key={category.code} value={category.code}>
            {category.name}
          </option>
        ))}
      </select>
    </label>
  )
}

export default CategoryFilter
