import { StatePanel } from "@/components/app-shell/state-panels";

export default function AdminLoading() {
  return <StatePanel message="Đang xác minh quyền và tải dữ liệu quản trị…" state="loading" />;
}
