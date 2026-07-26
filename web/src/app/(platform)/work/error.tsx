"use client";

import { useEffect } from "react";

export default function WorkError({
  error,
  reset
}: Readonly<{
  error: Error & { digest?: string };
  reset: () => void;
}>) {
  useEffect(() => {
    console.error("Work route failed", error.digest ?? "no-digest");
  }, [error.digest]);
  return (
    <section className="foundation-panel foundation-panel--narrow">
      <p className="eyebrow">Không thể mở công việc</p>
      <h1>Dữ liệu vận hành chưa sẵn sàng</h1>
      <p>
        Thử tải lại trong cùng phạm vi. Bản nhập chưa được lưu vào máy chủ và
        không có hàng chờ cục bộ nào được tạo.
      </p>
      {error.digest ? (
        <small className="state-panel__correlation">
          Mã lỗi: <span translate="no">{error.digest}</span>
        </small>
      ) : null}
      <button className="primary-action" onClick={reset} type="button">
        Thử lại
      </button>
    </section>
  );
}
