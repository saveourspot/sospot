import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

const NEGATIVE_COLOR = '#c95d47'
const POSITIVE_COLOR = '#9aa59f'

function formatGap(value) {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? `${numericValue.toFixed(1)}%p` : '-'
}

function RelativeGapChart({ categories = [] }) {
  if (categories.length === 0) {
    return (
      <div className="relative-gap-chart-pending" role="status">
        <div className="relative-gap-chart-pending__bars" aria-hidden="true">
          {[34, 58, 43, 71, 48, 28].map((width) => (
            <span key={width} style={{ width: `${width}%` }} />
          ))}
        </div>
        <strong>표시할 상대격차 데이터 없음</strong>
        <p>
          표본 기준을 충족한 대분류 업종의 상대격차를 확인할 수 없습니다.
        </p>
      </div>
    )
  }

  return (
    <div className="relative-gap-chart">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={categories}
          layout="vertical"
          margin={{ top: 8, right: 28, bottom: 24, left: 12 }}
        >
          <CartesianGrid stroke="#e8edea" strokeDasharray="3 3" horizontal={false} />
          <XAxis
            dataKey="relativeGap"
            type="number"
            tickFormatter={(value) => `${value}%p`}
          />
          <YAxis
            dataKey="categoryName"
            type="category"
            width={92}
            tick={{ fill: '#536159', fontSize: 12 }}
          />
          <Tooltip
            formatter={(value) => [formatGap(value), '상대격차']}
            labelFormatter={(label) => `업종: ${label}`}
          />
          <ReferenceLine x={0} stroke="#65736c" />
          <Bar dataKey="relativeGap" name="상대격차" radius={[0, 4, 4, 0]}>
            {categories.map((category) => (
              <Cell
                key={category.categoryCode ?? category.categoryName}
                fill={category.relativeGap < 0 ? NEGATIVE_COLOR : POSITIVE_COLOR}
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

export default RelativeGapChart
