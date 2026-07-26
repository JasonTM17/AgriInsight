import Link from "next/link";

import type { WorkActivity } from "../work-generated-client-adapter";
import {
  formatWorkInstant,
  workStatusLabel,
  workTypeLabel
} from "../work-format";
import {
  workHref,
  type WorkRouteState
} from "../work-route-state";

import styles from "./work-operations.module.css";

export function WorkActivityQueue({
  activities,
  hasMore,
  routeState
}: Readonly<{
  activities: readonly WorkActivity[];
  hasMore: boolean;
  routeState: WorkRouteState;
}>) {
  return (
    <section
      aria-labelledby="work-queue-title"
      className={styles.queue}
      data-testid="work-activity-queue"
    >
      <header className={styles.panelHeader}>
        <div>
          <p className="eyebrow">Phạm vi được cấp</p>
          <h2 id="work-queue-title">Hàng đợi công việc</h2>
        </div>
        <span>{activities.length} mục</span>
      </header>
      <ol className={styles.queueList}>
        {activities.map((activity) => {
          const selected = routeState.activityId === activity.id;
          return (
            <li key={activity.id}>
              <Link
                aria-current={selected ? "true" : undefined}
                className={`${styles.queueCard} ${
                  selected ? styles.queueCardSelected : ""
                }`}
                data-testid="work-activity-card"
                href={workHref(routeState, { activityId: activity.id })}
                prefetch={false}
              >
                <span className={styles.activityType}>
                  {workTypeLabel(activity.activityType)}
                </span>
                <strong>{activity.title}</strong>
                <small translate="no">{activity.code}</small>
                <span className={styles.queueMeta}>
                  <span>{workStatusLabel(activity.status)}</span>
                  <time dateTime={activity.dueAt}>
                    Hạn {formatWorkInstant(activity.dueAt)}
                  </time>
                </span>
              </Link>
            </li>
          );
        })}
      </ol>
      {hasMore ? (
        <p className={styles.boundaryNote}>
          Danh sách đang hiển thị 25 mục đầu trong phạm vi. Hãy dùng bộ lọc để
          thu hẹp kết quả.
        </p>
      ) : null}
    </section>
  );
}
