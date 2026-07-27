export const NAVIGATION_LABELS = {
  overview: "Tổng quan",
  farms: "Nông trại",
  work: "Công việc",
  inventory: "Tồn kho",
  costs: "Chi phí",
  cropHealth: "Sức khỏe cây trồng",
  dataQuality: "Chất lượng dữ liệu",
  assistant: "Trợ lý dữ liệu",
  administration: "Quản trị"
} as const;

export const NAVIGATION_DESCRIPTIONS = {
  overview: "Bức tranh vận hành theo phạm vi đã chọn.",
  farms: "Trang trại, khu vực và mùa vụ.",
  work: "Công việc, người phụ trách và hạn xử lý.",
  inventory: "Số dư, lô hàng và luồng nhập xuất.",
  costs: "Chi phí vận hành và mua hàng.",
  cropHealth: "Quan sát cây trồng và bằng chứng.",
  dataQuality: "Freshness, hợp lệ và hành động khắc phục.",
  assistant: "Hỏi đáp từ snapshot đã xác minh trong đúng phạm vi quyền.",
  administration: "Thành viên, vai trò và phạm vi tenant."
} as const;

export type NavigationKey = keyof typeof NAVIGATION_LABELS;

export const NAVIGATION_ORDER: readonly NavigationKey[] = [
  "overview",
  "farms",
  "work",
  "inventory",
  "costs",
  "cropHealth",
  "dataQuality",
  "assistant",
  "administration"
];

export const RECOVERY_MESSAGES = {
  loading: "Đang tải dữ liệu trong phạm vi hiện hành.",
  empty: "Chưa có dữ liệu trong phạm vi này. Hãy chọn phạm vi khác hoặc tạo bản ghi đầu tiên.",
  stale: "Dữ liệu đã cũ. Tải lại để kiểm tra phiên bản mới nhất.",
  partial: "Một phần dữ liệu chưa sẵn sàng. Bạn vẫn có thể xem phần đã đồng bộ.",
  denied: "Bạn không có quyền xem nội dung này. Liên hệ quản trị tenant nếu cần mở quyền.",
  offline: "Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.",
  conflict: "Dữ liệu vừa thay đổi ở nơi khác. Tải lại trước khi ghi tiếp.",
  failed: "Không thể tải dữ liệu. Thử lại với cùng phạm vi hoặc mở mã tương quan cho hỗ trợ."
} as const;

export type RecoveryState = keyof typeof RECOVERY_MESSAGES;
