export const ASSISTANT_SUGGESTIONS = [
  "Nông trại nào đang có chi phí vận hành cao nhất?",
  "Kho nào cần ưu tiên đặt thêm vật tư?",
  "Khu vực cây trồng nào cần được xem xét trước?"
] as const;

export const ASSISTANT_COPY = {
  eyebrow: "DeepSeek V4 Flash · RAG có kiểm soát",
  title: "Hỏi dữ liệu. Nhận câu trả lời có nguồn.",
  introduction:
    "Trợ lý chỉ tổng hợp từ snapshot AgriInsight đã xác minh và trong phạm vi bạn được cấp quyền. Mỗi kết luận đều phải dẫn đến bằng chứng.",
  privacy:
    "Câu hỏi được xử lý qua BFF bảo mật. Khóa nhà cung cấp và access token không được gửi xuống trình duyệt.",
  evidence:
    "Không có bằng chứng phù hợp đồng nghĩa với không có câu trả lời suy đoán."
} as const;
