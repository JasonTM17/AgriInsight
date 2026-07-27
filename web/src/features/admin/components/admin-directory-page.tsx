import Link from "next/link";

import { StatePanel } from "@/components/app-shell/state-panels";
import { ADMIN_STATUS_LABELS } from "@/content/vi/administration";

import { ADMIN_PAGE_SIZE } from "../admin-contract-schemas";
import type { loadAdminDirectory } from "../admin-read-model";
import { AdminCreateUserForm } from "./admin-create-user-form";
import styles from "./tenant-administration.module.css";

type Directory = Awaited<ReturnType<typeof loadAdminDirectory>>;

export function AdminDirectoryPage({
  directory,
  search,
  status
}: Readonly<{
  directory: Directory;
  search?: string;
  status: "active" | "all" | "inactive";
}>) {
  const previousOffset = Math.max(0, directory.offset - ADMIN_PAGE_SIZE);
  const nextOffset = directory.offset + ADMIN_PAGE_SIZE;
  return (
    <div className={styles.page} data-testid="admin-directory-page">
      <AdminCreateUserForm />
      <AdminDirectoryFilter search={search} status={status} />
      <section className={styles.panel}>
        <div className={styles.panelHeading}>
          <div>
            <p className="eyebrow">Tenant directory</p>
            <h2>Hồ sơ người dùng</h2>
          </div>
          <span>{directory.users.length} hồ sơ trong trang này</span>
        </div>
        {directory.users.length === 0 ? (
          <StatePanel
            actionHref="/admin"
            actionLabel="Xóa bộ lọc"
            message="Không có hồ sơ nào khớp bộ lọc hiện tại."
            state="empty"
          />
        ) : (
          <div className={styles.tableScroll}>
            <table className={styles.dataTable}>
              <thead>
                <tr>
                  <th scope="col">Người dùng</th>
                  <th scope="col">Liên hệ</th>
                  <th scope="col">Trạng thái</th>
                  <th scope="col">Phiên bản</th>
                  <th scope="col"><span className="sr-only">Thao tác</span></th>
                </tr>
              </thead>
              <tbody>
                {directory.users.map((user) => (
                  <tr key={user.userKey}>
                    <th scope="row">{user.displayName}</th>
                    <td>{user.contactLabel}</td>
                    <td><StatusPill status={user.status} /></td>
                    <td className={styles.numeric}>{user.version}</td>
                    <td><Link className={styles.textLink} href={`/admin/users/${user.userKey}`}>Mở hồ sơ</Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className={styles.pagination}>
          <span>Vị trí {directory.offset + 1}–{directory.offset + directory.users.length}</span>
          <div>
            {directory.offset > 0 ? (
              <Link href={directoryHref(previousOffset, status, search)}>Trang trước</Link>
            ) : null}
            {directory.hasMore ? (
              <Link href={directoryHref(nextOffset, status, search)}>Trang sau</Link>
            ) : null}
          </div>
        </div>
      </section>
    </div>
  );
}

function AdminDirectoryFilter({
  search,
  status
}: Readonly<{ search?: string; status: "active" | "all" | "inactive" }>) {
  return (
    <form action="/admin" className={styles.filterForm} method="get">
      <label>
        Tìm theo tên hoặc liên hệ
        <input defaultValue={search} maxLength={120} name="search" placeholder="Nhập từ khóa…" type="search" />
      </label>
      <label>
        Trạng thái
        <select defaultValue={status} name="status">
          <option value="active">Đang hoạt động</option>
          <option value="inactive">Đã vô hiệu</option>
          <option value="all">Tất cả</option>
        </select>
      </label>
      <button type="submit">Áp dụng</button>
      <Link href="/admin">Đặt lại</Link>
    </form>
  );
}

function StatusPill({ status }: Readonly<{ status: "active" | "inactive" }>) {
  return (
    <span className={status === "active" ? styles.activePill : styles.inactivePill}>
      {ADMIN_STATUS_LABELS[status]}
    </span>
  );
}

function directoryHref(
  offset: number,
  status: "active" | "all" | "inactive",
  search?: string
): string {
  const params = new URLSearchParams({ offset: String(offset), status });
  if (search) params.set("search", search);
  return `/admin?${params.toString()}`;
}
