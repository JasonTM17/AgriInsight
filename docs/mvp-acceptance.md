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
- [x] Named `big-data` profile reproducibly generates a nominal 1,051,200 sensor plan and a verified 1,050,000-row warehouse fact after quality fixtures; resolved configuration fingerprints the manifest run ID.

## Analytics và dashboard

- [x] Executive KPI: doanh thu, chi phí, lợi nhuận, biên lợi nhuận, sản lượng và cảnh báo.
- [x] Farm Performance: năng suất/ha, chi phí/ha, lợi nhuận và drill-down trang trại.
- [x] Inventory: tồn hiện tại, giá trị, low stock, stockout, overstock và expiry alert.
- [x] ABC Analysis theo tỷ trọng giá trị tồn kho.
- [x] Nhu cầu kho 30 ngày và recommended order quantity theo run-rate.
- [x] Dự báo nhu cầu kho 30 ngày deterministic có model/status/range/backtest,
  checksummed Gold, scoped API và browser evidence; tách khỏi run-rate policy,
  không tự tạo purchase order.
- [x] Crop Health: cảm biến, thời tiết, sâu bệnh, sensor freshness và risk score khu vực.
- [x] Data Quality dashboard hiển thị before/after, lỗi Bronze và remediation actions.
- [x] Cost Analysis lọc farm/crop/season/activity/month, hiển thị cost/budget/cost per unit, activity drivers và procurement lens riêng.
- [x] Insight và khuyến nghị có bằng chứng định lượng.
- [x] Dashboard render/navigation, form-submit, stale bundle, missing/empty Gold và export error được kiểm thử tự động.
- [x] Executive, Farm Performance, Inventory, Crop Health, Data Quality, Cost Analysis, Work, and Administration render contextual first-party visuals; the 14 hosted CI screenshots and 8 contextual AI visuals are tracked separately, missing assets fail soft, and Crop Health labels AI-generated demo evidence.

## Export service

- [x] Controlled report service và Phase 3 dashboard wiring: CSV/PDF chạy với `reports` extra; XLSX chỉ mở khi có explicit runtime vars; bundle chỉ xuất hiện sau submit.
- [x] XLSX QA thực tế có đủ 6 sheet, 6 preview, `MODEL STATUS: PASS`, và không có formula-error match.

## Vận hành

- [x] Có test suite end-to-end và CI workflow.
- [x] Có Dockerfile, Compose và hướng dẫn chạy.
- [x] Package wheel chứa SQL schema và CLI entrypoint.
- [x] Dataset mặc định hoàn thành trong thời gian phù hợp cho local demo/CI; guarded big-data runner keeps heavy temp/cache on D and verifies C/D twice.
- [x] Disk guard C/D chỉ đọc, có ngưỡng pass/warn/fail và test boundary/missing-drive.
- [x] Cost Analysis đã được browser QA ở desktop và narrow viewport, không có console/runtime error.
- [x] Browser smoke of the big-data dashboard verified Executive and Crop Health visual layout, caption, and evidence warning; Streamlit theme uses the Field Ledger tokens.

## Backlog của goal cấp dự án

- [ ] Custom Report Builder tự cấu hình ngoài ba format Cost Analysis đã kiểm soát.
- [ ] Hoàn tất external production deployment/recovery approvals của backend Phase 7; technical, CI, protected registry và restore evidence đã có, còn production OIDC/broker operations, RPO/RTO, off-host retention và owner approval.
- [x] Xây production frontend role-aware theo CK FE/Stitch design system và versioned OpenAPI contracts; hosted seven-persona gate và image `0.2.3` đã được xác minh.
- [ ] PostgreSQL/ClickHouse, Flyway/dbt và incremental ETL bằng Airflow.
- [ ] Broad semantic agriculture alerts, public alert API/UI, multi-channel notifications, and mobile field application. The private metadata-only alert-worker hardening is complete and released, but it intentionally implements none of these public/domain features.
- [ ] Forecasting nâng cao ngoài inventory baseline đã nghiệm thu, anomaly
  detection, what-if analysis và model monitoring.
- [ ] AI Assistant Text-to-SQL với guardrails và audit trail.

Checklist này xác nhận phase Data Analytics MVP; nó không thu hẹp phạm vi cuối cùng của AgriInsight.

## Backend hiện trạng

- [x] Backend Phase 1-6 đã nghiệm thu theo từng boundary.
- [x] Phase 5 warehouse/material/supplier/assignment/inventory ledger/reversal/reconciliation/RLS/OpenAPI đã qua 32 focused tests và full guarded backend gate (487 Surefire + 92 Failsafe; zero failures/errors/skips).
- [x] Hosted Java 21 CI, scan/SBOM/provenance, image scan/smoke, và backend/Python digest evidence đã pass 5/5 trên GitHub Actions run `29932250984`.

## Backend Phase 7 technical verification

- [x] Historical foundation: transactional outbox, typed schema v1, dedicated NOLOGIN integration role, and fenced lease/retry/ack boundary.
- [x] Isolated alert-worker hardening: immutable V22 baseline; V23-V28 with expected schema version 28; restricted `agriinsight_alert_worker` login; metadata-only scanner/observer; durable cursors; bounded recovery/hysteresis; and narrow RLS/grants. V23 backfill remains an enablement precondition, V27 is a readiness-only invalid-source-evidence index, and V28 is the forward acknowledgement-function repair. Hosted tests, review, merge, and protected publication passed for `v0.2.3`.
- [x] Pinned non-root Python/backend/web/analytics API images, allowlisted contexts, local Compose overlay, CI build-without-push và protected Docker Hub/GHCR publication; semantic `0.2.3` and full-SHA tags passed exact-digest scan/smoke in both registries.
- [x] D-local checksum/metadata backup và timed clean-target restore drill 11.045s; production RPO/RTO/off-host encryption/owner approval còn bắt buộc trước production.

## Backend Phase 7 production release gate

- [x] Protected tag-triggered image publication bằng environment secrets/reviewers; run `30413877863` published `v0.2.3` after main CI.
- [ ] Phê duyệt RPO/RTO/retention/encrypted off-host storage/restore owner và external production deployment.
- [x] New image tag/digest and Docker Hub/GHCR package publication are accepted for the alert-worker hardening through the shared backend image.
- [ ] Public alert center and external deployment remain outside the released worker-hardening boundary.
