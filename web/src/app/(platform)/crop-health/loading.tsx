import styles from "@/features/crop-quality/components/crop-quality.module.css";

export default function CropHealthLoading() {
  return (
    <div aria-busy="true" className={styles.page}>
      <div className={styles.panel}>
        <p className="eyebrow">Bằng chứng cây trồng</p>
        <h1>Đang xác minh snapshot…</h1>
        <p className={styles.muted}>Đang tải phạm vi khu vực và lineage của contract.</p>
      </div>
    </div>
  );
}
