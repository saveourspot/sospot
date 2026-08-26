import axios from 'axios'

export const apiClient = axios.create({
  baseURL: '/api',
})

function withOptionalPeriod(period) {
  return period == null || period === '' ? undefined : { period }
}

export async function getSummary(period) {
  const response = await apiClient.get('/summary', {
    params: withOptionalPeriod(period),
  })
  return response.data
}

export async function getRegionScores(period) {
  const response = await apiClient.get('/regions/scores', {
    params: withOptionalPeriod(period),
  })
  return response.data
}

export async function getAnomalyRegions(params = {}) {
  const response = await apiClient.get('/anomaly/regions', { params })
  return response.data
}

export async function getRegionDetail(dongCode, period) {
  const response = await apiClient.get(
    `/regions/${encodeURIComponent(dongCode)}`,
    { params: withOptionalPeriod(period) },
  )
  return response.data
}

export async function compareRegions(params = {}) {
  const response = await apiClient.get('/regions/compare', { params })
  return response.data
}

export async function getCategoryTrend(catCode, params = {}) {
  const response = await apiClient.get(
    `/categories/${encodeURIComponent(catCode)}/trend`,
    { params },
  )
  return response.data
}

export async function getBsi(periodMonth) {
  const response = await apiClient.get('/bsi', {
    params:
      periodMonth == null || periodMonth === '' ? undefined : { periodMonth },
  })
  return response.data
}

export async function getSelectedCategoryScores(catCodes, period) {
  const response = await apiClient.get('/anomaly/regions/selected-scores', {
    params: {
      catCodes: catCodes.join(','),
      ...(period ? { period } : {}),
    },
  })
  return response.data
}

export async function askAi(question) {
  const response = await apiClient.post('/ai/chat', { question })
  return response.data
}
