import { StatePanel } from "@/components/app-shell/state-panels";

import type {
  WorkSelectionState,
  WorkViewModel
} from "../load-work-view-model";
import {
  workHref,
  type WorkRouteState
} from "../work-route-state";
import { WorkActivityDetail } from "./work-activity-detail";
import { WorkActivityQueue } from "./work-activity-queue";
import { WorkFilterBar } from "./work-filter-bar";
import styles from "./work-operations.module.css";

export function WorkOperationsPage({
  canWriteLogs,
  correlationId,
  routeState,
  viewModel
}: Readonly<{
  canWriteLogs: boolean;
  correlationId: string;
  routeState: WorkRouteState;
  viewModel: WorkViewModel;
}>) {
  return (
    <div className={styles.page}>
      <header className={styles.pageHeading}>
        <div>
          <p className="eyebrow">Nhật ký hiện trường</p>
          <h1>Công việc trong phạm vi</h1>
          <p>
            Chỉ hiển thị hoạt động, phân công và lịch sử đã được máy chủ xác
            nhận cho phiên hiện tại.
          </p>
        </div>
        <div className={styles.trustNote}>
          <strong>Ghi trực tiếp, chống trùng</strong>
          <span>Không tạo hàng đợi cục bộ hoặc trạng thái đồng bộ giả.</span>
        </div>
      </header>
      <WorkFilterBar filters={routeState.filters} />
      <WorkBody
        canWriteLogs={canWriteLogs}
        correlationId={correlationId}
        routeState={routeState}
        viewModel={viewModel}
      />
    </div>
  );
}

function WorkBody({
  canWriteLogs,
  correlationId,
  routeState,
  viewModel
}: Readonly<{
  canWriteLogs: boolean;
  correlationId: string;
  routeState: WorkRouteState;
  viewModel: WorkViewModel;
}>) {
  if (viewModel.activities.status === "denied") {
    return (
      <StatePanel
        correlationId={correlationId}
        message="Phiên hiện tại không có quyền đọc công việc trong phạm vi này."
        state="denied"
      />
    );
  }
  if (viewModel.activities.status === "failure") {
    return (
      <StatePanel
        actionHref={workHref(routeState)}
        correlationId={correlationId}
        message={viewModel.activities.message}
        state="failed"
      />
    );
  }
  if (viewModel.activities.status === "empty") {
    return (
      <StatePanel
        actionHref="/work"
        actionLabel="Xóa bộ lọc"
        correlationId={correlationId}
        label="Không có công việc phù hợp"
        message="Máy chủ không trả về hoạt động nào trong phạm vi và bộ lọc hiện tại."
        state="empty"
      />
    );
  }
  return (
    <div
      className={`${styles.operationsGrid} ${
        viewModel.selection.status === "ready" ? styles.hasSelection : ""
      }`}
    >
      <WorkActivityQueue
        activities={viewModel.activities.items}
        hasMore={viewModel.activities.hasMore}
        routeState={routeState}
      />
      <SelectionContent
        canWriteLogs={canWriteLogs}
        correlationId={correlationId}
        routeState={routeState}
        selection={viewModel.selection}
      />
    </div>
  );
}

function SelectionContent({
  canWriteLogs,
  correlationId,
  routeState,
  selection
}: Readonly<{
  canWriteLogs: boolean;
  correlationId: string;
  routeState: WorkRouteState;
  selection: WorkSelectionState;
}>) {
  if (selection.status === "ready") {
    return (
      <WorkActivityDetail
        canWriteLogs={canWriteLogs}
        data={selection.data}
        routeState={routeState}
      />
    );
  }
  if (selection.status === "denied") {
    return (
      <StatePanel
        correlationId={correlationId}
        message={selection.message}
        state="denied"
      />
    );
  }
  if (selection.status === "failure") {
    return (
      <StatePanel
        actionHref={workHref(routeState)}
        correlationId={correlationId}
        message={selection.message}
        state="failed"
      />
    );
  }
  if (selection.status === "not-found") {
    return (
      <StatePanel
        actionHref={workHref(routeState)}
        actionLabel="Bỏ lựa chọn"
        correlationId={correlationId}
        message="Công việc được chọn không còn trong danh sách đã cấp quyền."
        state="failed"
      />
    );
  }
  return (
    <StatePanel
      actionHref={null}
      label="Chọn một công việc"
      message="Mở một mục trong hàng đợi để xem phân công, ghi nhật ký và tải lịch sử hiệu chỉnh."
      state="empty"
    />
  );
}
