export default function HomePage() {
  return (
    <section className="foundation-panel">
      <p className="eyebrow">Nền tảng dữ liệu nông nghiệp</p>
      <h1>Điều hành bằng dữ liệu có nguồn gốc rõ ràng.</h1>
      <p>
        Trình duyệt chỉ giữ cookie phiên mờ. Access token và refresh token được
        mã hóa, lưu phía máy chủ và không xuất hiện trong HTML hay JavaScript.
      </p>
      <div className="action-row">
        <a className="primary-action" href="/login">
          Đăng nhập vào hệ thống
        </a>
        <a className="secondary-action" href="/protected">
          Kiểm tra vùng bảo vệ
        </a>
      </div>
    </section>
  );
}
