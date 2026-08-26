import { Fragment, useState } from 'react'
import { askAi } from '../lib/api.js'

const RECOMMENDED_QUESTIONS = [
  '이번 분기에 먼저 살펴볼 지역을 알려줘',
  '판암1동의 최근 이상징후 근거를 보여줘',
  '목동 음식업과 대전 전체 흐름을 비교해줘',
  '목동에서 최근 흐름이 좋은 업종과 정책 검토 방향을 알려줘',
]

function renderInlineMarkdown(text) {
  return text.split(/(\*\*[^*]+\*\*|`[^`]+`)/g).map((part, index) => {
    if (part.startsWith('**') && part.endsWith('**')) {
      return <strong key={index}>{part.slice(2, -2)}</strong>
    }

    if (part.startsWith('`') && part.endsWith('`')) {
      return <code key={index}>{part.slice(1, -1)}</code>
    }

    return <Fragment key={index}>{part}</Fragment>
  })
}

function MarkdownAnswer({ children }) {
  const lines = children.replace(/\r\n/g, '\n').split('\n')
  const blocks = []
  let nextOrderedListNumber = 1

  for (let index = 0; index < lines.length;) {
    const line = lines[index].trim()

    if (!line) {
      index += 1
      continue
    }

    if (/^-{3,}$/.test(line)) {
      blocks.push(<hr key={`hr-${index}`} />)
      nextOrderedListNumber = 1
      index += 1
      continue
    }

    const heading = line.match(/^(#{1,3})\s+(.+)$/)
    if (heading) {
      const Heading = `h${Math.min(heading[1].length + 2, 5)}`
      blocks.push(
        <Heading key={`heading-${index}`}>
          {renderInlineMarkdown(heading[2])}
        </Heading>,
      )
      nextOrderedListNumber = 1
      index += 1
      continue
    }

    if (line.startsWith('>')) {
      const quoteLines = []
      while (index < lines.length && lines[index].trim().startsWith('>')) {
        quoteLines.push(lines[index].trim().replace(/^>\s?/, ''))
        index += 1
      }
      blocks.push(
        <blockquote key={`quote-${index}`}>
          {quoteLines.map((quoteLine, quoteIndex) => (
            <p key={quoteIndex}>{renderInlineMarkdown(quoteLine)}</p>
          ))}
        </blockquote>,
      )
      continue
    }

    const listItem = line.match(/^(\d+\.|-)\s+(.+)$/)
    if (listItem) {
      const ordered = listItem[1] !== '-'
      const items = []
      const pattern = ordered ? /^\d+\.\s+(.+)$/ : /^-\s+(.+)$/

      while (index < lines.length) {
        const match = lines[index].trim().match(pattern)
        if (!match) break
        items.push(match[1])
        index += 1
      }

      const List = ordered ? 'ol' : 'ul'
      const listStart = ordered ? nextOrderedListNumber : undefined
      blocks.push(
        <List key={`list-${index}`} start={listStart}>
          {items.map((item, itemIndex) => (
            <li key={itemIndex}>{renderInlineMarkdown(item)}</li>
          ))}
        </List>,
      )
      if (ordered) {
        nextOrderedListNumber += items.length
      }
      continue
    }

    const paragraph = [line]
    index += 1
    while (index < lines.length && lines[index].trim()) {
      const nextLine = lines[index].trim()
      if (/^(#{1,3})\s+|^-{3,}$|^>|^(\d+\.|-)\s+/.test(nextLine)) break
      paragraph.push(nextLine)
      index += 1
    }
    blocks.push(
      <p key={`paragraph-${index}`}>
        {renderInlineMarkdown(paragraph.join(' '))}
      </p>,
    )
  }

  return <div className="ai-answer__content">{blocks}</div>
}

function AiPromptBox() {
  const [question, setQuestion] = useState('')
  const [response, setResponse] = useState(null)
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  const submitQuestion = async (nextQuestion) => {
    const trimmedQuestion = nextQuestion.trim()

    if (!trimmedQuestion || isLoading) {
      return
    }

    setQuestion(trimmedQuestion)
    setError('')
    setResponse(null)
    setIsLoading(true)

    try {
      setResponse(await askAi(trimmedQuestion))
    } catch (requestError) {
      const message = requestError.response?.data?.message
      setError(
        message ||
          '답변을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
      )
    } finally {
      setIsLoading(false)
    }
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    submitQuestion(question)
  }

  return (
    <section className="ai-prompt" aria-labelledby="ai-prompt-heading">
      <div className="ai-prompt__intro">
        <p className="eyebrow">SOSpot AI</p>
        <h2 id="ai-prompt-heading">대전 상권, 무엇이 궁금한가요?</h2>
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
          <button type="submit" disabled={!question.trim() || isLoading}>
            {isLoading ? '분석 중…' : '질문하기'}
          </button>
        </div>
        <p className="ai-prompt__privacy-notice">
          공개 상권 분석용 기능입니다. 개인정보나 민감정보를 입력하지 마세요.
        </p>
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
                submitQuestion(recommendedQuestion)
              }}
              disabled={isLoading}
            >
              {recommendedQuestion}
            </button>
          ))}
        </div>
      </div>

      <div className="ai-prompt__response" aria-live="polite">
        {isLoading && (
          <p className="ai-prompt__status">분석 API에서 근거를 확인하고 있습니다.</p>
        )}
        {error && <p className="ai-prompt__error" role="alert">{error}</p>}
        {response && (
          <article className="ai-answer">
            <div className="ai-answer__heading">
              <strong>답변</strong>
              <span>{response.mode === 'llm' ? 'AI 응답' : 'API 기반 안내'}</span>
            </div>
            <MarkdownAnswer>{response.answer}</MarkdownAnswer>
            {response.toolCalls?.length > 0 && (
              <details className="ai-answer__evidence">
                <summary>분석 근거 {response.toolCalls.length}건</summary>
                <ul>
                  {response.toolCalls.map((toolCall, index) => (
                    <li key={`${toolCall.name}-${index}`}>
                      <strong>{toolCall.name}</strong>
                      <dl>
                        {Object.entries(toolCall.args || {}).map(([key, value]) => (
                          <div key={key}>
                            <dt>{key}</dt>
                            <dd>{String(value)}</dd>
                          </div>
                        ))}
                      </dl>
                    </li>
                  ))}
                </ul>
              </details>
            )}
            <p className="ai-answer__notice">
              점포 수 감소가 개별 점포의 폐업을 의미하지 않습니다.
            </p>
          </article>
        )}
      </div>
    </section>
  )
}

export default AiPromptBox
