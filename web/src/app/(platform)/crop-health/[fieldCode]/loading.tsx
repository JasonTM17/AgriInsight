import styles from "@/features/crop-quality/components/crop-quality.module.css";

export default function CropHealthFieldLoading() {
  return (
    <div aria-busy="true" className={styles.page}>
      <div className={styles.panel}>
        <p className="eyebrow">Chi tiết khu vực</p>
        <h1>Đang tải bằng chứng…</h1>
      </div>
    </div>
  );
}
