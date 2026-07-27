import type { AssistantAnswer } from "../assistant-contract";
import { AssistantClientError } from "../assistant-api-client";
import styles from "./assistant-workspace.module.css";

type AssistantResponsePanelProps = Readonly<{
  answer: AssistantAnswer | null;
  error: AssistantClientError | null;
  loading: boolean;
  onRetry: () => void;
}>;

const sourceLabels: Readonly<Record<string, string>> = {
  "crop-health": "Sức khỏe cây trồng",
  "farm-performance": "Hiệu quả nông trại",
  cost: "Chi phí",
  inventory: "Tồn kho",
  overview: "Tổng quan",
  procurement: "Mua hàng",
  "data-quality": "Chất lượng dữ liệu"
};

export function AssistantResponsePanel({
  answer,
  error,
  loading,
  onRetry
}: AssistantResponsePanelProps) {
  if (loading) {
    return (
      <div className={styles.loadingState} role="status">
        <span aria-hidden="true" className={styles.loadingMark} />
        <div>
          <strong>Đang đối chiếu bằng chứng</strong>
          <p>Truy xuất snapshot trong phạm vi quyền và kiểm tra trích dẫn.</p>
        </div>
      </div>
    );
  }
  if (error) {
    return (
      <div className={styles.errorState} role="alert">
        <p className="eyebrow">Không thể hoàn tất truy vấn</p>
        <h2>{error.message}</h2>
        {error.correlationId ? (
          <p className={styles.correlation}>
            Mã tương quan: <code translate="no">{error.correlationId}</code>
          </p>
        ) : null}
        <button onClick={onRetry} type="button">Thử lại câu hỏi</button>
      </div>
    );
  }
  if (!answer) {
    return (
      <div className={styles.initialState}>
        <p className="eyebrow">Nguyên tắc trả lời</p>
        <h2>Bằng chứng trước, kết luận sau.</h2>
        <p>
          Trợ lý không duyệt web và không tự mở rộng phạm vi dữ liệu. Hãy hỏi
          về hiệu quả nông trại, chi phí, tồn kho hoặc sức khỏe cây trồng.
        </p>
      </div>
    );
  }
  return (
    <article className={styles.answer}>
      <div className={styles.answerHeading}>
        <div>
          <p className="eyebrow">
            {answer.status === "answered"
              ? "Câu trả lời đã đối chiếu"
              : "Chưa đủ bằng chứng"}
          </p>
          <h2>Kết quả từ snapshot</h2>
        </div>
        <span>{answer.citations?.length ?? 0} nguồn</span>
      </div>
      <p className={styles.answerText}>{answer.answer}</p>
      {(answer.citations?.length ?? 0) > 0 ? (
        <ol aria-label="Bằng chứng được trích dẫn" className={styles.citations}>
          {answer.citations?.map((citation) => (
            <li key={citation.evidenceId}>
              <div className={styles.citationMeta}>
                <span>{sourceLabels[citation.sourceType]}</span>
                <time dateTime={citation.asOf}>
                  Dữ liệu đến {formatDate(citation.asOf)}
                </time>
              </div>
              <strong>{citation.title}</strong>
              <p>{citation.excerpt}</p>
              <code translate="no">[{citation.evidenceId}]</code>
            </li>
          ))}
        </ol>
      ) : null}
      <details className={styles.usage}>
        <summary>Thông tin xử lý</summary>
        <p>
          {answer.usage.totalTokens.toLocaleString("vi-VN")} token ·{" "}
          {answer.usage.promptCacheHitTokens.toLocaleString("vi-VN")} token
          được dùng lại từ cache
        </p>
      </details>
    </article>
  );
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    timeZone: "UTC"
  }).format(new Date(`${value}T00:00:00Z`));
}
