import Link from "next/link";

export default function NotFoundPage() {
  return (
    <main
      className="foundation-panel foundation-panel--narrow"
      id="main-content"
      tabIndex={-1}
    >
      <p className="eyebrow">404 · Không tìm thấy trang</p>
      <h1>Đường dẫn này không tồn tại.</h1>
      <p>
        Nội dung có thể đã được di chuyển hoặc bạn chưa có đường dẫn chính xác.
        Hãy quay về điểm bắt đầu an toàn của AgriInsight.
      </p>
      <Link className="primary-action" href="/">
        Về trang chủ
      </Link>
    </main>
  );
}
