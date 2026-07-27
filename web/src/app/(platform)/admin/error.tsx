"use client";

import { useEffect } from "react";

import { Button } from "@/components/ui/button";

export default function AdminError({
  error,
  reset
}: Readonly<{ error: Error & { digest?: string }; reset: () => void }>) {
  useEffect(() => console.error(error), [error]);
  return (
    <section className="state-panel state-panel--failed" role="alert">
      <div>
        <p className="eyebrow">Quản trị tenant</p>
        <h2>Không thể dựng khu vực quản trị</h2>
        <p>Thử tải lại để xác minh phiên và contract dữ liệu.</p>
        <Button onClick={reset} variant="outline">Tải lại</Button>
      </div>
    </section>
  );
}
