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

const tooltipFormatter = (value, name) => {
  if (name === '대전 체감 BSI') {
    return [Number(value).toFixed(1), name]
  }

  return [`${Number(value).toLocaleString('ko-KR')}개`, name]
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
        <strong>추세 데이터 API 연동 대기</strong>
        <p>실제 3분기 지역·대전 점포 수와 대전 체감 BSI가 연결되면 표시됩니다.</p>
      </div>
    )
  }

  return (
    <div className="trend-chart">
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={series} margin={{ top: 12, right: 10, left: 0, bottom: 4 }}>
          <CartesianGrid stroke="#e6ebe8" strokeDasharray="3 3" />
          <XAxis dataKey="period" tick={{ fill: '#68766f', fontSize: 12 }} />
          <YAxis
            yAxisId="stores"
            tick={{ fill: '#68766f', fontSize: 11 }}
            tickFormatter={(value) => value.toLocaleString('ko-KR')}
            width={52}
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
            yAxisId="stores"
            type="monotone"
            dataKey="regionCount"
            name="해당 지역"
            stroke="#176b52"
            strokeWidth={3}
            dot={{ r: 4 }}
          />
          <Line
            yAxisId="stores"
            type="monotone"
            dataKey="cityCount"
            name="대전 전체 동일 업종"
            stroke="#607b9e"
            strokeWidth={2.5}
            strokeDasharray="6 4"
            dot={{ r: 3 }}
          />
        </ComposedChart>
      </ResponsiveContainer>
      {bsiPeriodLabel && <p className="trend-chart__bsi-period">BSI 기준: {bsiPeriodLabel}</p>}
    </div>
  )
}

export default TrendChart
