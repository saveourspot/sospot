import {
  Area,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { formatPeriod } from '../lib/periodFormat.js'

const tooltipFormatter = (value, name) => {
  if (name === '대전 체감 BSI') {
    return [Number(value).toFixed(1), name]
  }

  return [Number(value).toFixed(1), name]
}

function TrendChart({ series = [], bsiPeriodLabel }) {
  if (series.length === 0) {
    return (
      <div className="trend-chart-pending" role="status">
        <div className="trend-chart-pending__plot" aria-hidden="true">
          <span />
          <span />
          <span />
          <span />
        </div>
        <strong>표시할 추세 데이터 없음</strong>
        <p>유효한 3분기 지역·대전 점포 수 흐름을 확인할 수 없습니다.</p>
      </div>
    )
  }

  const firstRegionCount = series[0].regionCount
  const firstCityCount = series[0].cityCount
  const indexedSeries = series.map((point) => ({
    ...point,
    regionIndex:
      firstRegionCount > 0 ? (point.regionCount / firstRegionCount) * 100 : null,
    cityIndex: firstCityCount > 0 ? (point.cityCount / firstCityCount) * 100 : null,
  }))

  return (
    <div className="trend-chart">
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={indexedSeries} margin={{ top: 12, right: 10, left: 0, bottom: 4 }}>
          <CartesianGrid stroke="#e6ebe8" strokeDasharray="3 3" />
          <XAxis
            dataKey="period"
            tick={{ fill: '#68766f', fontSize: 12 }}
            tickFormatter={formatPeriod}
          />
          <YAxis
            yAxisId="change"
            tick={{ fill: '#68766f', fontSize: 11 }}
            tickFormatter={(value) => value.toFixed(0)}
            width={52}
            domain={['auto', 'auto']}
          />
          <YAxis
            yAxisId="bsi"
            orientation="right"
            tick={{ fill: '#9a7a38', fontSize: 11 }}
            width={38}
          />
          <Tooltip formatter={tooltipFormatter} />
          <Legend verticalAlign="top" height={42} />
          <Area
            yAxisId="bsi"
            type="monotone"
            dataKey="bsi"
            name="대전 체감 BSI"
            stroke="#c59b49"
            fill="#efd9a7"
            fillOpacity={0.3}
            connectNulls
          />
          <Line
            yAxisId="change"
            type="monotone"
            dataKey="regionIndex"
            name="해당 지역 (첫 분기=100)"
            stroke="#0072b2"
            strokeWidth={3.5}
            dot={{ r: 4.5, fill: '#ffffff', stroke: '#0072b2', strokeWidth: 3 }}
            activeDot={{ r: 6, fill: '#0072b2' }}
          />
          <Line
            yAxisId="change"
            type="monotone"
            dataKey="cityIndex"
            name="대전 전체 동일 업종 (첫 분기=100)"
            stroke="#d55e00"
            strokeWidth={3}
            strokeDasharray="9 6"
            dot={false}
            activeDot={{ r: 6, fill: '#d55e00' }}
          />
        </ComposedChart>
      </ResponsiveContainer>
      {bsiPeriodLabel && <p className="trend-chart__bsi-period">BSI 기준: {bsiPeriodLabel}</p>}
    </div>
  )
}

export default TrendChart
