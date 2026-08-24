const baseUrl = process.env.AI_EVAL_BASE_URL || 'http://localhost:8081'

const cases = [
  ['A01', '최근 이상징후 큰 지역 알려줘', 'searchAnomalyRegions', {}, ['지역'], []],
  ['A02', '중점검토 지역 상위 5개 알려줘', 'searchAnomalyRegions', { grade: '중점검토', topN: 5 }, ['중점검토'], []],
  ['A03', '어디를 먼저 살펴봐야 해?', 'searchAnomalyRegions', {}, [], []],
  ['B01', '음식업이 안 좋은 지역 알려줘', 'searchAnomalyRegions', { catCode: 'I2' }, ['음식'], []],
  ['B02', '식당 쪽 상황 안 좋은 동네는?', 'searchAnomalyRegions', { catCode: 'I2' }, [], []],
  ['B03', '교육업 이상징후 큰 곳 알려줘', 'searchAnomalyRegions', { catCode: 'P1' }, ['교육'], []],
  ['C01', '목동은 왜 중점검토야?', 'getRegionDetail', { dongCode: '30140550' }, ['목동'], []],
  ['C02', '판암1동 상황 알려줘', 'getRegionDetail', { dongCode: '30110551' }, ['판암1동'], []],
  ['C03', '대흥동에서 이상징후 큰 업종이 뭐야?', 'getRegionDetail', { dongCode: '30140575' }, ['대흥동'], []],
  ['D01', '목동이랑 대흥동 비교해줘', 'compareRegions', { dongA: '30140550', dongB: '30140575' }, ['목동', '대흥동'], []],
  ['D02', '목동과 온천2동 중 어디가 더 이상징후가 커?', 'compareRegions', {}, ['목동', '온천2동'], []],
  ['D03', '목동과 대흥동 음식업 비교하고 차이도 설명해줘', 'compareRegions', { catCode: 'I2' }, ['목동', '대흥동'], []],
  ['E01', '온천2는 어때?', 'getRegionDetail', { dongCode: '30200540' }, ['온천2동'], []],
  ['E02', '목동 식당 상황은?', 'getRegionDetail', { dongCode: '30140550' }, ['목동'], []],
  ['E03', '학원 쪽 안 좋은 데 어디야?', 'searchAnomalyRegions', { catCode: 'P1' }, [], []],
  ['F01', '어디가 제일 안 좋아?', 'searchAnomalyRegions', {}, [], ['폐업률', '폐업 확률']],
  ['F02', '음식은?', 'searchAnomalyRegions', { catCode: 'I2' }, [], []],
  ['F03', '여기는 괜찮아?', null, {}, ['지역'], []],
  ['G01', '목동 폐업률 알려줘', null, {}, ['제공하지 않습니다'], ['폐업률은']],
  ['G02', '앞으로 망할 동네 어디야?', null, {}, ['미래 예측'], ['확률']],
  ['H01', '2026년 2분기 기준으로 중점검토 지역 알려줘', 'searchAnomalyRegions', { period: '202606' }, ['2026.06'], ['202606']],
  ['H02', '2024년 상황 알려줘', null, {}, ['분석 결과가 없습니다'], []],
  ['I01', '서울 강남구 분석해줘', null, {}, ['대전광역시 82개 행정동만'], []],
  ['I02', '부산 음식업 상황 알려줘', null, {}, ['대전광역시 82개 행정동만'], []],
  ['J01', '최근 대전 체감경기 어때?', 'getBsiContext', {}, ['BSI'], []],
  ['J02', '목동 음식점업 BSI 알려줘', null, {}, ['교차한 값은 존재하지 않습니다'], []],
  ['K01', '목동 표본 부족 업종 알려줘', 'getRegionDetail', { dongCode: '30140550' }, ['표본 부족'], []],
  ['L01', '중점검토 지역 중 음식업도 감소한 곳 알려줘', 'searchAnomalyRegions', { catCode: 'I2', grade: '중점검토' }, ['음식'], []],
  ['M01', '목동이랑 대흥동 식당 비교 좀', 'compareRegions', { catCode: 'I2' }, ['목동', '대흥동'], []],
  ['M02', '음식점 안조은 동내 알려줘', 'searchAnomalyRegions', { catCode: 'I2' }, [], []],
]

const started = Date.now()
const results = []
for (const [id, question, expectedTool, expectedArgs, required, forbidden] of cases) {
  const caseStarted = Date.now()
  let response
  let error
  try {
    const http = await fetch(`${baseUrl}/api/ai/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': `eval-${id}` },
      body: JSON.stringify({ question }),
    })
    response = await http.json()
    if (!http.ok) error = `HTTP ${http.status}`
  } catch (caught) {
    error = caught.message
  }

  const calls = response?.toolCalls || []
  const selected = calls.map((call) => call.name)
  const toolCorrect = expectedTool === null
    ? selected.length === 0
    : selected.includes(expectedTool)
  const targetCall = calls.find((call) => call.name === expectedTool)
  const parametersCorrect = Object.entries(expectedArgs).every(
    ([key, value]) => String(targetCall?.args?.[key]) === String(value),
  )
  const answer = response?.answer || ''
  const requiredCorrect = required.every((text) => answer.includes(text))
  const forbiddenCorrect = forbidden.every((text) => !answer.includes(text))
  const rawPeriodLeak = /\b20\d{4}\b/.test(answer)
  const p0 = !forbiddenCorrect || rawPeriodLeak
  const passed = !error && toolCorrect && parametersCorrect && requiredCorrect && !p0
  results.push({
    id, question, passed, p0, mode: response?.mode, selected,
    toolCorrect, parametersCorrect, requiredCorrect, forbiddenCorrect,
    rawPeriodLeak, latencyMs: Date.now() - caseStarted, answer, error,
  })
  process.stdout.write(`${id} ${passed ? 'PASS' : 'FAIL'} ${selected.join(',') || '-'} ${Date.now() - caseStarted}ms\n`)
}

const total = results.length
const passed = results.filter((item) => item.passed).length
const expectedToolCases = results.filter((_, index) => cases[index][2] !== null)
const expectedParameterCases = results.filter((_, index) => Object.keys(cases[index][3]).length > 0)
const summary = {
  total,
  passed,
  failed: total - passed,
  passRate: Number((passed / total * 100).toFixed(1)),
  p0Failures: results.filter((item) => item.p0).length,
  toolSelectionAccuracy: Number((expectedToolCases.filter((item) => item.toolCorrect).length / expectedToolCases.length * 100).toFixed(1)),
  parameterAccuracy: Number((expectedParameterCases.filter((item) => item.parametersCorrect).length / expectedParameterCases.length * 100).toFixed(1)),
  groundingFailures: results.filter((item) => item.rawPeriodLeak).length,
  hallucinations: results.filter((item) => !item.forbiddenCorrect).length,
  averageToolCalls: Number((results.reduce((sum, item) => sum + item.selected.length, 0) / total).toFixed(2)),
  averageLatencyMs: Math.round(results.reduce((sum, item) => sum + item.latencyMs, 0) / total),
  elapsedMs: Date.now() - started,
}
process.stdout.write(`AI_EVAL_RESULT=${JSON.stringify({ summary, failures: results.filter((item) => !item.passed) })}\n`)
