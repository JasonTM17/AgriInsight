export const ADMINISTRATION_COPY = Object.freeze({
  auditDescription:
    "Theo dõi thay đổi định danh và phân quyền bằng dấu thời gian UTC, kết quả và mã tương quan.",
  auditTitle: "Nhật ký quản trị",
  directoryDescription:
    "Quản lý vòng đời tài khoản, vai trò và phạm vi nông trại hoặc kho trong tenant hiện tại.",
  directoryTitle: "Quản trị tenant",
  identityNotice:
    "Mã chủ thể OIDC là dữ liệu nhạy cảm: chỉ nhập khi liên kết và không hiển thị lại.",
  scopeNotice:
    "Mọi thay đổi đều yêu cầu quyền quản trị, chống CSRF, idempotency và kiểm tra phiên bản."
});

export const ADMIN_STATUS_LABELS = Object.freeze({
  active: "Đang hoạt động",
  inactive: "Đã vô hiệu"
});

export const ADMIN_AUDIT_OUTCOME_LABELS = Object.freeze({
  CONFLICT: "Xung đột",
  DENIED: "Bị từ chối",
  SUCCEEDED: "Thành công"
});
