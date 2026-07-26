import { StatePanel } from "@/components/app-shell/state-panels";

import type { WorkHistoryState } from "../load-work-view-model";
import type { WorkActivityLog } from "../work-generated-client-adapter";
import {
  workHref,
  type WorkRouteState
} from "../work-route-state";
import { CorrectWorkLogForm } from "./correct-work-log-form";
import { WorkLogEntry } from "./work-log-entry";
import styles from "./work-operations.module.css";
import { WorkPageControls } from "./work-page-controls";

export function WorkLogLineagePanel({
  activityId,
  canWriteLogs,
  history,
  routeState,
  selectedLog
}: Readonly<{
  activityId: string;
  canWriteLogs: boolean;
  history: WorkHistoryState;
  routeState: WorkRouteState;
  selectedLog?: WorkActivityLog;
}>) {
  if (history.status === "not-found" || !selectedLog) {
    return (
      <StatePanel
        actionHref={workHref(routeState, {
          activityId,
          logOffset: routeState.logOffset
        })}
        actionLabel="Bỏ lựa chọn"
        message="Bản ghi được chọn không còn trong danh sách hiện hành."
        state="failed"
      />
    );
  }
  if (history.status === "denied") {
    return <StatePanel message={history.message} state="denied" />;
  }
  if (history.status === "failure") {
    return (
      <StatePanel
        actionHref={workHref(routeState, {
          activityId,
          logOffset: routeState.logOffset
        })}
        message={history.message}
        state="failed"
      />
    );
  }
  if (history.status === "unselected") return null;
  return (
    <section aria-labelledby="lineage-title" className={styles.lineagePanel}>
      <div className={styles.sectionHeading}>
        <div>
          <p className="eyebrow">Lịch sử máy chủ</p>
          <h3 id="lineage-title">Chuỗi hiệu chỉnh bất biến</h3>
        </div>
        <span>
          {history.status === "empty"
            ? "0 sự kiện"
            : `Sự kiện ${history.offset + 1}–${
                history.offset + history.items.length
              }`}
        </span>
      </div>
      {history.status === "empty" ? (
        <EmptyHistoryPage
          activityId={activityId}
          history={history}
          routeState={routeState}
          selectedLog={selectedLog}
        />
      ) : (
        <>
          <ol className={styles.lineageList}>
            {history.items.map((entry, index) => (
              <li data-testid="work-lineage-entry" key={entry.id}>
                <span>{history.offset + index + 1}</span>
                <WorkLogEntry log={entry} />
              </li>
            ))}
          </ol>
          <WorkPageControls
            ariaLabel="Phân trang lịch sử hiệu chỉnh"
            hasMore={history.hasMore}
            hrefForOffset={(offset) =>
              workHref(routeState, {
                activityId,
                historyOffset: offset,
                logId: selectedLog.id,
                logOffset: routeState.logOffset
              })}
            itemCount={history.items.length}
            limit={history.limit}
            offset={history.offset}
          />
          {canWriteLogs ? (
            <CorrectWorkLogForm
              activityId={activityId}
              key={selectedLog.id}
              log={selectedLog}
            />
          ) : (
            <StatePanel
              actionHref={null}
              label="Chế độ chỉ đọc"
              message="Phiên hiện tại không có quyền nối bản hiệu chỉnh."
              state="denied"
            />
          )}
        </>
      )}
    </section>
  );
}

function EmptyHistoryPage({
  activityId,
  history,
  routeState,
  selectedLog
}: Readonly<{
  activityId: string;
  history: Extract<WorkHistoryState, { status: "empty" }>;
  routeState: WorkRouteState;
  selectedLog: WorkActivityLog;
}>) {
  const hasPriorPage = history.offset > 0;
  return (
    <StatePanel
      actionHref={
        hasPriorPage
          ? workHref(routeState, {
              activityId,
              historyOffset: Math.max(0, history.offset - history.limit),
              logId: selectedLog.id,
              logOffset: routeState.logOffset
            })
          : workHref(routeState, {
              activityId,
              logOffset: routeState.logOffset
            })
      }
      actionLabel={hasPriorPage ? "Về trang lịch sử trước" : "Bỏ lựa chọn"}
      label={hasPriorPage ? "Trang lịch sử đã trống" : "Lịch sử chưa sẵn sàng"}
      message={
        hasPriorPage
          ? "Không có thêm sự kiện trong trang này."
          : "Máy chủ chưa trả về chuỗi sự kiện nên hiệu chỉnh đang bị khóa."
      }
      state="failed"
    />
  );
}

