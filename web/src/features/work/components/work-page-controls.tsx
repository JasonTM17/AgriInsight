import Link from "next/link";

import { WORK_MAX_OFFSET } from "../work-generated-client-adapter";

import styles from "./work-operations.module.css";

export function WorkPageControls({
  ariaLabel,
  hasMore,
  hrefForOffset,
  itemCount,
  limit,
  offset
}: Readonly<{
  ariaLabel: string;
  hasMore: boolean;
  hrefForOffset: (offset: number) => string;
  itemCount: number;
  limit: number;
  offset: number;
}>) {
  if (offset === 0 && !hasMore) return null;
  const canLoadNext = hasMore && offset + limit <= WORK_MAX_OFFSET;
  return (
    <nav aria-label={ariaLabel} className={styles.pagination}>
      {offset > 0 ? (
        <Link
          href={hrefForOffset(Math.max(0, offset - limit))}
          prefetch={false}
        >
          ← Trang trước
        </Link>
      ) : <span />}
      <span>
        {offset + 1}–{offset + itemCount}
      </span>
      {canLoadNext ? (
        <Link href={hrefForOffset(offset + limit)} prefetch={false}>
          Trang sau →
        </Link>
      ) : hasMore ? <span>Đã đạt giới hạn máy chủ</span> : <span />}
    </nav>
  );
}
