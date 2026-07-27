"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";

import {
  AssistantClientError,
  queryAssistant
} from "../assistant-api-client";
import type {
  AssistantAnswer,
  ConversationTurn
} from "../assistant-contract";
import { ASSISTANT_COPY, ASSISTANT_SUGGESTIONS } from "../assistant-copy";
import { AssistantResponsePanel } from "./assistant-response-panel";
import styles from "./assistant-workspace.module.css";

export function AssistantWorkspace() {
  const [question, setQuestion] = useState("");
  const [history, setHistory] = useState<readonly ConversationTurn[]>([]);
  const [answer, setAnswer] = useState<AssistantAnswer | null>(null);
  const [error, setError] = useState<AssistantClientError | null>(null);
  const [loading, setLoading] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);
  const resultRef = useRef<HTMLDivElement>(null);

  useEffect(() => () => controllerRef.current?.abort(), []);
  useEffect(() => {
    if (!loading && (answer || error)) resultRef.current?.focus();
  }, [answer, error, loading]);

  async function submitQuestion(nextQuestion: string) {
    const normalized = nextQuestion.trim();
    if (!normalized || loading) return;
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    setQuestion(normalized);
    setAnswer(null);
    setError(null);
    setLoading(true);
    try {
      const nextAnswer = await queryAssistant(
        {
          question: normalized,
          history: history.slice(-6)
        },
        controller.signal
      );
      setAnswer(nextAnswer);
      setHistory((current) => {
        const turns: ConversationTurn[] = [
          ...current,
          { role: "user", content: normalized },
          { role: "assistant", content: nextAnswer.answer.slice(0, 2_000) }
        ];
        return turns.slice(-6);
      });
    } catch (cause) {
      if (controller.signal.aborted) return;
      setError(
        cause instanceof AssistantClientError
          ? cause
          : new AssistantClientError(
            "assistant_unavailable",
            502,
            "Không thể kết nối trợ lý dữ liệu."
          )
      );
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void submitQuestion(question);
  }

  return (
    <div className={styles.page} data-testid="assistant-workspace">
      <header className={styles.hero}>
        <div>
          <p className="eyebrow">{ASSISTANT_COPY.eyebrow}</p>
          <h1>{ASSISTANT_COPY.title}</h1>
          <p>{ASSISTANT_COPY.introduction}</p>
        </div>
        <aside aria-label="Ranh giới tin cậy" className={styles.trustNote}>
          <strong>Ranh giới tin cậy</strong>
          <span>{ASSISTANT_COPY.evidence}</span>
        </aside>
      </header>

      <div className={styles.workspace}>
        <section aria-labelledby="assistant-question-heading" className={styles.composer}>
          <div>
            <p className="eyebrow">Một câu hỏi mỗi lần</p>
            <h2 id="assistant-question-heading">Bạn muốn biết điều gì?</h2>
          </div>
          <form onSubmit={onSubmit}>
            <label htmlFor="assistant-question">
              Câu hỏi về dữ liệu nông nghiệp
            </label>
            <textarea
              aria-describedby="assistant-question-help"
              autoComplete="off"
              disabled={loading}
              id="assistant-question"
              maxLength={1_200}
              name="question"
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="Ví dụ: Kho nào đang có số ngày cung ứng thấp nhất?"
              rows={6}
              value={question}
            />
            <div className={styles.composerFooter}>
              <span id="assistant-question-help">
                {question.length.toLocaleString("vi-VN")} / 1.200 ký tự
              </span>
              <button disabled={loading || question.trim().length === 0} type="submit">
                {loading ? "Đang đối chiếu…" : "Hỏi từ dữ liệu"}
              </button>
            </div>
          </form>
          <div className={styles.suggestions}>
            <p>Gợi ý bắt đầu</p>
            {ASSISTANT_SUGGESTIONS.map((suggestion) => (
              <button
                disabled={loading}
                key={suggestion}
                onClick={() => {
                  setQuestion(suggestion);
                  void submitQuestion(suggestion);
                }}
                type="button"
              >
                {suggestion}
              </button>
            ))}
          </div>
          <p className={styles.privacyNote}>{ASSISTANT_COPY.privacy}</p>
        </section>

        <section
          aria-label="Kết quả trợ lý"
          aria-live="polite"
          className={styles.result}
          ref={resultRef}
          tabIndex={-1}
        >
          <AssistantResponsePanel
            answer={answer}
            error={error}
            loading={loading}
            onRetry={() => void submitQuestion(question)}
          />
        </section>
      </div>
    </div>
  );
}
