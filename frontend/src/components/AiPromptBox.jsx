import { useState } from 'react'

const RECOMMENDED_QUESTIONS = [
  '이번 분기에 먼저 살펴볼 지역을 알려줘',
  '판암1동의 최근 이상징후 근거를 보여줘',
  '목동 음식업과 대전 전체 흐름을 비교해줘',
]

function AiPromptBox() {
  const [question, setQuestion] = useState('')

  const prepareQuestion = (nextQuestion) => {
    const trimmedQuestion = nextQuestion.trim()

    if (!trimmedQuestion) {
      return
    }

    sessionStorage.setItem('sospot.pendingQuestion', trimmedQuestion)
    window.alert('AI 질의응답 기능은 다음 단계에서 연결됩니다.')
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    prepareQuestion(question)
  }

  return (
    <section className="ai-prompt" aria-labelledby="ai-prompt-heading">
      <div className="ai-prompt__intro">
        <p className="eyebrow">SOSpot AI</p>
        <h2 id="ai-prompt-heading">분석 결과를 자연어로 물어보세요</h2>
        <p>
          지역과 업종을 함께 질문하면 실제 분석 API 결과를 바탕으로 근거를
          설명합니다.
        </p>
      </div>

      <form className="ai-prompt__form" onSubmit={handleSubmit}>
        <label className="sr-only" htmlFor="ai-question">
          SOSpot AI 질문
        </label>
        <div className="ai-prompt__input-row">
          <input
            id="ai-question"
            type="text"
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="예: 이번 분기에 먼저 살펴볼 지역은 어디야?"
          />
          <button type="submit" disabled={!question.trim()}>
            질문하기
          </button>
        </div>
      </form>

      <div className="ai-prompt__recommendations" aria-label="추천 질문">
        <span>추천 질문</span>
        <div>
          {RECOMMENDED_QUESTIONS.map((recommendedQuestion) => (
            <button
              key={recommendedQuestion}
              type="button"
              onClick={() => {
                setQuestion(recommendedQuestion)
                prepareQuestion(recommendedQuestion)
              }}
            >
              {recommendedQuestion}
            </button>
          ))}
        </div>
      </div>
    </section>
  )
}

export default AiPromptBox
