import { StatePanel } from "@/components/app-shell/state-panels";

export default function WorkLoading() {
  return (
    <StatePanel
      actionHref={null}
      label="Đang tải công việc"
      message="Hệ thống đang đọc phạm vi hoạt động và nhật ký đã được máy chủ xác nhận."
      state="loading"
    />
  );
}
