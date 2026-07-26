import { Icon } from "@/components/ui/icon";
import { RECOVERY_MESSAGES, type RecoveryState } from "@/content/vi/navigation";

const STATE_LABELS: Record<RecoveryState, string> = {
  loading: "Đang tải",
  empty: "Chưa có dữ liệu",
  stale: "Dữ liệu đã cũ",
  partial: "Đang đồng bộ một phần",
  denied: "Không có quyền",
  offline: "Ngoại tuyến",
  conflict: "Xung đột phiên bản",
  failed: "Tải dữ liệu thất bại"
};

const STATE_ICONS: Record<RecoveryState, "refresh" | "search" | "shield-check" | "x"> = {
  loading: "refresh",
  empty: "search",
  stale: "refresh",
  partial: "refresh",
  denied: "shield-check",
  offline: "x",
  conflict: "refresh",
  failed: "x"
};

export function StatePanel({
  state,
  correlationId,
  actionHref = "/protected",
  actionLabel = "Thử lại",
  label,
  message
}: {
  state: RecoveryState;
  correlationId?: string;
  actionHref?: string | null;
  actionLabel?: string;
  label?: string;
  message?: string;
}) {
  return (
    <section aria-live={state === "loading" ? "polite" : undefined} className={`state-panel state-panel--${state}`}>
      <span className="state-panel__icon">
        <Icon name={STATE_ICONS[state]} size={22} />
      </span>
      <div>
        <p className="eyebrow">{label ?? STATE_LABELS[state]}</p>
        <p>{message ?? RECOVERY_MESSAGES[state]}</p>
        {correlationId ? <small className="state-panel__correlation">Mã tương quan: <span translate="no">{correlationId}</span></small> : null}
        {state !== "loading" && state !== "denied" && actionHref ? (
          <a className="text-action" href={actionHref}>
            {actionLabel} <Icon name="arrow-right" size={16} />
          </a>
        ) : null}
      </div>
    </section>
  );
}
