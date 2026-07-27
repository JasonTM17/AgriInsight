import { StatePanel } from "./state-panels";

export function PlatformForbidden() {
  return (
    <StatePanel
      actionHref={null}
      label="Truy cập bị từ chối"
      message="Phiên hiện tại không có quyền truy cập khu vực này. Yêu cầu đã dừng với trạng thái HTTP 403."
      state="denied"
    />
  );
}
