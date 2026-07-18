# Tiêu chí nghiệm thu Data Analytics MVP

## Data foundation

- [x] Dataset giả lập có seed và ngày chốt dữ liệu cố định.
- [x] Có dữ liệu trang trại, mùa vụ, chăm sóc, thu hoạch, kho, IoT, thời tiết và sâu bệnh.
- [x] Có lỗi data quality chủ ý để chứng minh cleaning/quarantine.
- [x] Bronze, Silver và quarantine tách biệt.
- [x] Data quality report đo completeness, validity, uniqueness và freshness.
- [x] Silver đạt 100% các quality gate bắt buộc trước khi load.
- [x] Warehouse dùng star schema và không vi phạm khóa ngoại.
- [x] Pipeline idempotent, reproducible và có manifest/checksum.

## Analytics và dashboard

- [x] Executive KPI: doanh thu, chi phí, lợi nhuận, biên lợi nhuận, sản lượng và cảnh báo.
- [x] Farm Performance: năng suất/ha, chi phí/ha, lợi nhuận và drill-down trang trại.
- [x] Inventory: tồn hiện tại, giá trị, low stock, stockout, overstock và expiry alert.
- [x] ABC Analysis theo tỷ trọng giá trị tồn kho.
- [x] Nhu cầu kho 30 ngày và recommended order quantity theo run-rate.
- [x] Crop Health: cảm biến, thời tiết, sâu bệnh, sensor freshness và risk score khu vực.
- [x] Data Quality dashboard hiển thị before/after, lỗi Bronze và remediation actions.
- [x] Insight và khuyến nghị có bằng chứng định lượng.
- [x] Dashboard render/navigation được kiểm thử tự động.

## Vận hành

- [x] Có test suite end-to-end và CI workflow.
- [x] Có Dockerfile, Compose và hướng dẫn chạy.
- [x] Package wheel chứa SQL schema và CLI entrypoint.
- [x] Dataset mặc định hoàn thành trong thời gian phù hợp cho local demo/CI.

## Backlog của goal cấp dự án

- [ ] Cost Analysis dashboard chuyên sâu theo supplier/activity/season với drill-down giao dịch.
- [ ] Custom Report Builder và xuất PDF được kiểm soát.
- [ ] Backend nghiệp vụ, authentication và row-level authorization.
- [ ] PostgreSQL/ClickHouse, Flyway/dbt và incremental ETL bằng Airflow.
- [ ] Realtime Kafka, cảnh báo đa kênh và mobile field application.
- [ ] ML forecasting, anomaly detection, what-if analysis và model monitoring.
- [ ] AI Assistant Text-to-SQL với guardrails và audit trail.

Checklist này xác nhận phase Data Analytics MVP; nó không thu hẹp phạm vi cuối cùng của AgriInsight.

