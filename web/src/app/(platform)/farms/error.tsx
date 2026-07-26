"use client";

import { useEffect } from "react";

export default function FarmsError({
  error,
  reset
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("Farms route failed", error.digest ?? "no-digest");
  }, [error.digest]);
  return (
    <section className="foundation-panel foundation-panel--narrow">
      <p className="eyebrow">Không thể mở nông trại</p>
      <h1>Dữ liệu chưa sẵn sàng</h1>
      <p>Thử tải lại trong cùng phạm vi. Nếu lỗi tiếp diễn, gửi mã lỗi bên dưới cho đội vận hành.</p>
      {error.digest ? <small className="state-panel__correlation">Mã lỗi: <span translate="no">{error.digest}</span></small> : null}
      <button className="primary-action" onClick={reset} type="button">Thử lại</button>
    </section>
  );
}
