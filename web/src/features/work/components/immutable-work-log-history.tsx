import Link from "next/link";

import { StatePanel } from "@/components/app-shell/state-panels";

import type {
  WorkCollectionState,
  WorkHistoryState
} from "../load-work-view-model";
import type { WorkActivityLog } from "../work-generated-client-adapter";
import {
  workHref,
  type WorkRouteState
} from "../work-route-state";
import { WorkLogEntry } from "./work-log-entry";
import { WorkLogLineagePanel } from "./work-log-lineage-panel";
import styles from "./work-operations.module.css";
import { WorkPageControls } from "./work-page-controls";

export function ImmutableWorkLogHistory({
  activityId,
  canWriteLogs,
  history,
  logs,
  routeState
}: Readonly<{
  activityId: string;
  canWriteLogs: boolean;
  history: WorkHistoryState;
  logs: WorkCollectionState<WorkActivityLog>;
  routeState: WorkRouteState;
}>) {
  if (logs.status === "denied") {
    return <StatePanel message={logs.message} state="denied" />;
  }
  if (logs.status === "failure") {
    return (
      <StatePanel
        actionHref={workHref(routeState, {
          activityId,
          logOffset: routeState.logOffset
        })}
        message={logs.message}
        state="failed"
      />
    );
  }
  if (logs.status === "empty") {
    return <EmptyLogPage activityId={activityId} logs={logs} routeState={routeState} />;
  }
  const selectedLog = routeState.logId
    ? logs.items.find((log) => log.id === routeState.logId)
    : undefined;
  return (
    <div className={styles.historyStack}>
      <ol aria-label="Nhật ký bất biến" className={styles.logList}>
        {logs.items.map((log) => (
          <li data-testid="work-log-entry" key={log.id}>
            <WorkLogEntry log={log} />
            <Link
              className={styles.lineageLink}
              href={workHref(routeState, {
                activityId,
                logId: log.id,
                logOffset: logs.offset
              })}
              prefetch={false}
            >
              Xem lịch sử và hiệu chỉnh
            </Link>
          </li>
        ))}
      </ol>
      <WorkPageControls
        ariaLabel="Phân trang nhật ký"
        hasMore={logs.hasMore}
        hrefForOffset={(offset) =>
          workHref(routeState, { activityId, logOffset: offset })}
        itemCount={logs.items.length}
        limit={logs.limit}
        offset={logs.offset}
      />
      {routeState.logId ? (
        <WorkLogLineagePanel
          activityId={activityId}
          canWriteLogs={canWriteLogs}
          history={history}
          routeState={routeState}
          selectedLog={selectedLog}
        />
      ) : (
        <p className={styles.boundaryNote}>
          Chọn “Xem lịch sử và hiệu chỉnh” để tải chuỗi sự kiện từ máy chủ.
        </p>
      )}
    </div>
  );
}

function EmptyLogPage({
  activityId,
  logs,
  routeState
}: Readonly<{
  activityId: string;
  logs: Extract<WorkCollectionState<WorkActivityLog>, { status: "empty" }>;
  routeState: WorkRouteState;
}>) {
  if (logs.offset > 0) {
    return (
      <StatePanel
        actionHref={workHref(routeState, {
          activityId,
          logOffset: Math.max(0, logs.offset - logs.limit)
        })}
        actionLabel="Về trang nhật ký trước"
        label="Trang nhật ký đã trống"
        message="Không có thêm bản ghi trong trang này."
        state="empty"
      />
    );
  }
  return (
    <StatePanel
      actionHref={null}
      label="Chưa có nhật ký"
      message="Công việc này chưa có bản ghi nào được máy chủ xác nhận."
      state="empty"
    />
  );
}
