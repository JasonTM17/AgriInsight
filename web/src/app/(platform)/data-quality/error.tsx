"use client";

import { useEffect } from "react";

import { Button } from "@/components/ui/button";
import styles from "@/features/crop-quality/components/crop-quality.module.css";

export default function DataQualityError({
  error,
  reset
}: Readonly<{ error: Error & { digest?: string }; reset: () => void }>) {
  useEffect(() => console.error(error), [error]);
  return (
    <div className={styles.panel} role="alert">
      <p className="eyebrow">Kiểm soát dữ liệu</p>
      <h2>Không thể dựng trang chất lượng dữ liệu</h2>
      <p>Thử tải lại để xác minh lại phiên và snapshot dữ liệu.</p>
      <Button onClick={reset} variant="outline">Tải lại</Button>
    </div>
  );
}
