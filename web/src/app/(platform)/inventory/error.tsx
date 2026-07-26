"use client";

import { useEffect } from "react";

import { Button } from "@/components/ui/button";
import styles from "@/features/inventory/components/inventory-control.module.css";

export default function InventoryError({
  error,
  reset
}: Readonly<{
  error: Error & { digest?: string };
  reset: () => void;
}>) {
  useEffect(() => {
    console.error(error);
  }, [error]);
  return (
    <div className={styles.degradedPanel} role="alert">
      <p className="eyebrow">Inventory control</p>
      <h2>Không thể dựng trang kho</h2>
      <p>Thử tải lại để xác minh lại phiên và phạm vi dữ liệu.</p>
      <Button onClick={reset} variant="outline">Tải lại</Button>
    </div>
  );
}
