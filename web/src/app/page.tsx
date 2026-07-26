import Link from "next/link";

export default function HomePage() {
  return (
    <main className="entry-page" id="main-content" tabIndex={-1}>
      <header className="entry-page__header">
        <Link className="brand" href="/">
          <span aria-hidden="true" className="brand-mark" />
          AgriInsight
        </Link>
        <span className="foundation-label">Hệ thống vận hành</span>
      </header>
      <section className="entry-page__hero">
        <p className="eyebrow">Nền tảng dữ liệu nông nghiệp</p>
        <h1>Điều hành mùa vụ bằng dữ liệu có nguồn gốc rõ ràng.</h1>
        <p>
          Một không gian làm việc chung cho trang trại, công việc, tồn kho,
          chi phí và chất lượng dữ liệu — trong đúng phạm vi tenant của bạn.
        </p>
        <div className="action-row">
          <Link className="primary-action" href="/login">
            Đăng nhập vào hệ thống
          </Link>
          <Link className="secondary-action" href="/overview">
            Mở tổng quan
          </Link>
        </div>
      </section>
    </main>
  );
}
