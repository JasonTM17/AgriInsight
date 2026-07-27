import { StatePanel } from "@/components/app-shell/state-panels";

export default function AdminForbidden() {
  return (
    <StatePanel
      actionHref={null}
      label="Truy cập quản trị bị từ chối"
      message="Phiên hiện tại không có quyền quản trị tenant. Yêu cầu đã dừng với trạng thái HTTP 403."
      state="denied"
    />
  );
}
