import Link from "next/link";

import { StatePanel } from "@/components/app-shell/state-panels";

import type { WorkSelectedActivity } from "../load-work-view-model";
import {
  formatWorkInstant,
  shortWorkId,
  workStatusLabel,
  workTypeLabel
} from "../work-format";
import {
  workHref,
  type WorkRouteState
} from "../work-route-state";
import { AppendWorkLogForm } from "./append-work-log-form";
import { ImmutableWorkLogHistory } from "./immutable-work-log-history";
import styles from "./work-operations.module.css";

export function WorkActivityDetail({
  canWriteLogs,
  data,
  routeState
}: Readonly<{
  canWriteLogs: boolean;
  data: WorkSelectedActivity;
  routeState: WorkRouteState;
}>) {
  const { activity, assignments, logs, history } = data;
  return (
    <section
      aria-labelledby="work-detail-title"
      className={styles.detail}
      data-testid="work-activity-detail"
    >
      <Link
        className={styles.mobileBack}
        href={workHref(routeState)}
        prefetch={false}
      >
        ← Hàng đợi công việc
      </Link>
      <header className={styles.detailHeader}>
        <div>
          <span className={styles.activityType}>
            {workTypeLabel(activity.activityType)}
          </span>
          <h2 id="work-detail-title">{activity.title}</h2>
          <p>{activity.description ?? "Không có mô tả bổ sung."}</p>
        </div>
        <span className={styles.statusChip}>
          {workStatusLabel(activity.status)}
        </span>
      </header>
      <dl className={styles.activityContext}>
        <div>
          <dt>Mã công việc</dt>
          <dd translate="no">{activity.code}</dd>
        </div>
        <div>
          <dt>Hạn xử lý</dt>
          <dd>
            <time dateTime={activity.dueAt}>
              {formatWorkInstant(activity.dueAt)}
            </time>
          </dd>
        </div>
        <div>
          <dt>Khu vực</dt>
          <dd translate="no">{shortWorkId(activity.fieldId)}</dd>
        </div>
        <div>
          <dt>Mùa vụ</dt>
          <dd translate="no">{shortWorkId(activity.seasonId)}</dd>
        </div>
      </dl>
      <section className={styles.detailSection}>
        <div className={styles.sectionHeading}>
          <div>
            <p className="eyebrow">Phân công máy chủ</p>
            <h3>Nhân sự đang được giao</h3>
          </div>
          {assignments.status === "ready" ? (
            <span>{assignments.items.length} nhân sự</span>
          ) : null}
        </div>
        <AssignmentContent
          activityId={activity.id}
          assignments={assignments}
          canWriteLogs={canWriteLogs}
          routeState={routeState}
        />
      </section>
      <section className={styles.detailSection} id="work-log-history">
        <div className={styles.sectionHeading}>
          <div>
            <p className="eyebrow">Không ghi đè</p>
            <h3>Nhật ký và lịch sử hiệu chỉnh</h3>
          </div>
        </div>
        <ImmutableWorkLogHistory
          activityId={activity.id}
          canWriteLogs={canWriteLogs}
          history={history}
          logs={logs}
          routeState={routeState}
        />
      </section>
    </section>
  );
}

function AssignmentContent({
  activityId,
  assignments,
  canWriteLogs,
  routeState
}: Readonly<{
  activityId: string;
  assignments: WorkSelectedActivity["assignments"];
  canWriteLogs: boolean;
  routeState: WorkRouteState;
}>) {
  if (assignments.status === "denied") {
    return <StatePanel message={assignments.message} state="denied" />;
  }
  if (assignments.status === "failure") {
    return (
      <StatePanel
        actionHref={workHref(routeState, { activityId })}
        message={assignments.message}
        state="failed"
      />
    );
  }
  if (assignments.status === "empty") {
    return (
      <StatePanel
        actionHref={null}
        label="Chưa có phân công"
        message="Không thể ghi nhật ký cho đến khi máy chủ có phân công đang hoạt động."
        state="empty"
      />
    );
  }
  if (!canWriteLogs) {
    return (
      <>
        <AssignmentList assignments={assignments.items} />
        <StatePanel
          actionHref={null}
          label="Chế độ chỉ đọc"
          message="Phiên hiện tại có thể xem phân công nhưng không có quyền ghi nhật ký."
          state="denied"
        />
      </>
    );
  }
  return (
    <>
      <AssignmentList assignments={assignments.items} />
      <AppendWorkLogForm
        activityId={activityId}
        assignments={assignments.items}
        key={activityId}
      />
    </>
  );
}

function AssignmentList({
  assignments
}: Readonly<{
  assignments: Extract<
    WorkSelectedActivity["assignments"],
    { status: "ready" }
  >["items"];
}>) {
  return (
    <ul className={styles.assignmentList}>
      {assignments.map((assignment) => (
        <li key={assignment.id}>
          <strong translate="no">
            Nhân sự {shortWorkId(assignment.employeeId)}
          </strong>
          <small translate="no">
            Phân công {shortWorkId(assignment.id)}
          </small>
        </li>
      ))}
    </ul>
  );
}
