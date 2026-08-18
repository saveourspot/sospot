import { GRADE_ORDER } from './gradeStyles.js'

// 지도 색상과 조인 동작을 확인하기 위한 UI 전용 mock이다.
// 실제 등급은 API 응답으로 교체하며 이 배열 순서는 분석 의미가 없다.
export function createPreviewRegionScores(features, categoryCode = '') {
  const categoryOffset = categoryCode
    ? [...categoryCode].reduce((sum, character) => sum + character.charCodeAt(0), 0)
    : 0

  return features.map((feature, index) => ({
    dongCode: feature.properties.dong_code,
    grade: GRADE_ORDER[(index + categoryOffset) % GRADE_ORDER.length],
    sampleSizeFlag: 'OK',
  }))
}
