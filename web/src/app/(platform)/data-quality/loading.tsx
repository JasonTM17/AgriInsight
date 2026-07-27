import styles from "@/features/crop-quality/components/crop-quality.module.css";

export default function DataQualityLoading() {
  return (
    <div aria-busy="true" className={styles.page}>
      <div className={styles.panel}>
        <p className="eyebrow">Kiểm soát dữ liệu</p>
        <h1>Đang xác minh chất lượng…</h1>
        <p className={styles.muted}>Đang tải kết quả kiểm tra và remediation từ snapshot.</p>
      </div>
    </div>
  );
}
