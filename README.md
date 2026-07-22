# AgriInsight

AgriInsight là nền tảng phân tích dữ liệu cho doanh nghiệp nông nghiệp. Phiên bản `0.2.0` cung cấp một Data Analytics MVP chạy end-to-end và có thể tái lập:

```text
Operational simulators → Bronze → Validation & quarantine → Silver
                       → Star-schema warehouse → Gold KPI/alerts
                       → Evidence-backed insight → Multi-domain dashboard
```

## Phạm vi đang chạy được

- Quản lý dữ liệu doanh nghiệp, trang trại, khu vực, cây trồng, mùa vụ, hoạt động chăm sóc và thu hoạch.
- Quản lý kho vật tư, nhà cung cấp, nhập/xuất kho, tồn hiện tại, days-of-supply, nhu cầu 30 ngày và ABC Analysis.
- Thu thập dữ liệu cảm biến, thời tiết và quan sát sâu bệnh; tính risk score cho từng khu vực.
- Chèn lỗi nguồn có chủ ý: trùng khóa, mã không chuẩn, đơn vị tấn/kg, số âm, giá trị cảm biến ngoài phạm vi và dữ liệu thiếu.
- Tách Bronze, Silver và quarantine; đo completeness, validity, uniqueness, freshness trước và sau xử lý.
- Nạp warehouse SQLite theo star schema, kiểm tra toàn bộ khóa ngoại trước khi thay thế database hiện hành.
- Vật hóa Gold contracts cho Executive, Farm Performance, Inventory, Cost Analysis, Crop Health và Data Quality.
- Sinh cảnh báo cùng khuyến nghị có bằng chứng dữ liệu; UI không tự tính lại logic KPI.
- Chạy lặp lại an toàn theo seed/ngày chốt dữ liệu, có manifest, row count và SHA-256 checksum.

## Backend vận hành đang triển khai

Backend Java 21/Spring Boot nằm riêng trong `backend/`. Phase 1-6 đã được nghiệm thu đến ngày 2026-07-22; Phase 7 đang đóng transactional outbox và release hardening. Backend hiện có application foundation, deny-by-default OIDC security, exact identity bootstrap, database-backed roles/permissions, tenant/profile-scoped transactions, PostgreSQL FORCE RLS, durable idempotency/audit, farm-to-harvest APIs, inventory/procurement APIs với warehouse assignment, immutable ledger/projections, reversals, reconciliation và OpenAPI contracts, cùng operating-cost ledger V16-V17 với correction lineage và bounded summaries.

Phase 7 bổ sung V18-V19 `outbox_events`, event schema v1, fenced lease/retry/dead-letter, role `agriinsight_integration`, optional backend Compose profile, pinned non-root images, CI image gate, protected Docker Hub/GHCR publication workflow và D-local backup/restore wrappers. Outbox chưa có consumer/Kafka/HTTP route; đây là handoff an toàn, at-least-once cho phase kế tiếp.

Bằng chứng hiện tại:

- Disk guard PASS trước các tác vụ nặng; guarded Maven `verify` đạt 622 test (gồm 98 Failsafe integration test) trên PostgreSQL 18 sạch, zero failures/errors/skips, gồm Flyway V1-V19 apply/validate, fresh install, RLS, assignment lifecycle, cost correction concurrency, outbox lease/dead-letter, query plans và reconciliation.
- OIDC kiểm tra signature/asymmetric algorithm, issuer, API audience, `exp`, `nbf`, subject và access-token discriminator; `(iss, sub)` được resolve chính xác, rồi profile/tenant/role/permission được nạp dưới tenant context mà không tin JWT role/tenant claim.
- Public route chỉ gồm health allowlist. `/api/v1/me` và các route quản trị user/external identity/role dùng exact method/template + permission; mapping chưa đăng ký bị deny. Security response/log không chứa raw token hoặc provider diagnostics.
- Runtime database role không phải owner/superuser/`BYPASSRLS`; tenant context chỉ tồn tại trong transaction. Direct-SQL tests chứng minh thiếu/sai context, cross-tenant read/write, `WITH CHECK` và pooled-connection reuse đều fail closed.
- Mutation quản trị dùng optimistic version + canonical idempotency bound theo tenant/principal/route; user/identity/role lifecycle, last-admin invariant, conflict và authorization-denial audit đều được kiểm thử.
- Farm API hiện có list/get/create/update/deactivate/reactivate với exact route permissions, assignment-aware visibility, `ETag`/`If-Match`, canonical idempotency và response không lộ `tenantId`.
- Farm deactivation fail closed khi còn field, season, activity hoặc assignment đang hoạt động. Giao dịch chạy explicit READ_COMMITTED; Flyway V7 dùng trigger ở cả farm cha và live child để serialize hai thứ tự cạnh tranh, đồng thời kiểm tra dữ liệu V6→V7 trước nâng cấp mà không làm yếu ENABLE/FORCE RLS khi rollback.
- Phase 4 cung cấp field/crop/season, Employee, farm/activity assignment, task lifecycle, log công việc bất biến và harvest ledger. Manager bị giới hạn theo farm assignment; worker chỉ thấy task được giao và chỉ append/correct log của chính mình; harvest chuẩn hóa KG/TONNE về kg và sửa sai bằng bản ghi correction thay vì ghi đè.
- Local JDK mới hơn biên dịch bằng `--release 21`; multi-stage image dùng Temurin 21, chạy non-root `10001:10001`, chỉ chứa `/app/app.jar` và đã qua smoke test liveness/readiness/fail-closed OIDC.
- Regression analytics đạt 76 test pass, 3 test PDF skip có chủ đích khi thiếu optional report extras; compileall, Node syntax, Compose config và wheel build đều đạt.
- Không để lại smoke/Testcontainers PostgreSQL container; các container dự án khác không bị dọn. Upstream `postgres:18.0-alpine` vẫn chỉ là dependency kiểm thử.

Các cổng còn mở thuộc phase sau:

- Phase 7 core đã có focused atomicity/lease/RLS tests; full guarded acceptance và protected registry release vẫn là gate cuối của phase. Vì vậy toàn sản phẩm chưa production-ready. Identity vẫn mặc định tắt cho đến khi deployment cung cấp đầy đủ OIDC contract.
- Registry release yêu cầu repository variable `DOCKERHUB_NAMESPACE`, environment secrets `DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN` và reviewer protection; không có automatic `latest`. Workflow xuất cả Docker Hub và GHCR, scan exact digest rồi smoke-test digest.
- PostgreSQL 18 chỉ được lấy từ upstream cho integration test, tuyệt đối không republish dưới namespace AgriInsight.

Xem [báo cáo nghiệm thu Backend Phase 1](./plans/260719-0753-backend-auth-rbac/reports/acceptance-2026-07-19-backend-phase1.md), [Backend Phase 2](./plans/260719-0753-backend-auth-rbac/reports/acceptance-2026-07-20-backend-phase2.md), [Backend Phase 3](./plans/260719-0753-backend-auth-rbac/reports/acceptance-2026-07-20-backend-phase3.md), [Backend Phase 4](./plans/260719-0753-backend-auth-rbac/reports/acceptance-2026-07-22-backend-phase4.md), [Backend Phase 5](./plans/260719-0753-backend-auth-rbac/reports/acceptance-2026-07-22-backend-phase5.md), [Backend Phase 6](./plans/260719-0753-backend-auth-rbac/reports/acceptance-2026-07-22-backend-phase6.md), [backend development](docs/backend-development.md) và [backend deployment/recovery](docs/backend-deployment.md).

Lệnh kiểm thử backend chuẩn từ repository root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify
```

Wrapper chạy disk guard trước Maven, buộc Maven repo/temp/user-home nằm trên ổ D, từ chối hidden `MAVEN_ARGS`/`MAVEN_CONFIG`/`MAVEN_PROJECTBASEDIR` và dừng trước khi build nếu C/D không cùng PASS. Chỉ image first-party đã vượt đủ test/review/release gate mới được push lên Docker Hub; không republish PostgreSQL hay image upstream.
Lệnh `verify` còn yêu cầu Docker daemon sẵn sàng và từ chối các cờ `skipTests`, `skipIT`, `fail-never`, cũng như POM/settings/module selector thay thế.

## Chạy cục bộ

Yêu cầu Python 3.11–3.14.

```powershell
python -m pip install -e ".[dev,dashboard,reports]"
python -m agriinsight run --output artifacts
streamlit run dashboard/app.py
```

Dashboard mặc định mở tại `http://localhost:8501`. Navigation bên trái gồm:

- Executive
- Farm Performance
- Inventory
- Crop Health
- Data Quality
- Cost Analysis

Cost Analysis có hai lens tách biệt: chi phí vận hành và mua hàng. Form vận hành lọc theo nông trại/cây trồng/mùa vụ/hoạt động/tháng; form mua hàng lọc theo nông trại/nhà cung cấp/tháng. Chỉ khi submit form, service mới tạo các nút CSV/PDF và capability-gated XLSX từ request đã chuẩn hóa. PDF cục bộ cần `reports` extra như lệnh cài đặt trên. XLSX chỉ khả dụng khi provision explicit `AGRIINSIGHT_NODE_EXECUTABLE` và `AGRIINSIGHT_NODE_MODULES`.

Frontend discovery cho Inventory Control có fixture chỉ đọc, cố định phạm vi `WH-001`, đối soát 10 cảnh báo và 15 SKU-location từ Gold/Silver. Source/static/browser/print/review gates đã hoàn tất; đây chưa phải màn hình production. Frontend production có thể lập kế hoạch trên read/mutation contracts Phase 5 nhưng vẫn phải giữ warehouse scope, idempotency, ETag và Phase 6 dependency. Xem [`inventory-control-review.md`](./plans/260719-0753-backend-auth-rbac/design-system/prototypes/inventory-control-review.md).

Dashboard hiện là công cụ local/internal; chưa có authentication, RBAC hoặc row-level authorization. Không public port 8501 ra Internet trước khi milestone bảo mật hoàn thành.

Pipeline mặc định tạo 6 trang trại, 24 khu vực, 15 loại vật tư và khoảng 11.500 sensor readings. Có thể tạo dataset gần một triệu readings bằng cấu hình lớn hơn:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1
python -m agriinsight run --output artifacts `
  --farms 10 --fields-per-farm 12 `
  --sensor-days 365 --sensor-readings-per-day 24 `
  --materials 18
```

## Chạy bằng Docker

```powershell
docker compose up --build pipeline
docker compose up --build dashboard
```

Backend local/staging là profile riêng, không khởi động cùng pipeline/dashboard:

```powershell
Copy-Item .env.example .env.backend.local
docker compose --env-file .env.backend.local -f compose.yaml -f compose.backend.yaml --profile backend config --quiet
docker compose --env-file .env.backend.local -f compose.yaml -f compose.backend.yaml --profile backend up --build backend
```

Profile này bind PostgreSQL/API trên loopback, lưu database ở `backend/.runtime/postgres` trên D và chạy role bootstrap → Flyway → runtime restricted. Xem [backend deployment](docs/backend-deployment.md) trước khi dùng.

Docker Desktop cần được khởi động trước. Dashboard chỉ publish tại
`127.0.0.1:8501`; Gold được mount read-only, còn `artifacts/_tmp` là overlay
writable riêng cho report temp. Artifact vẫn được lưu trong `artifacts/` trên host.

## Kiểm thử

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1
python -m pytest
python -m compileall -q src dashboard tests
docker compose -f compose.yaml config --quiet
python -m pip wheel . --no-deps --no-build-isolation --wheel-dir artifacts/_tmp/wheel
```

Test suite kiểm tra pipeline end-to-end, idempotency, reproducibility, foreign keys, KPI reconciliation, export limits, disk thresholds, form-submit boundary và render/navigation của cả sáu dashboard.

## Cấu trúc artifact

```text
artifacts/
├── bronze/       # dữ liệu nguồn, giữ nguyên lỗi có chủ ý
├── silver/       # dữ liệu đã chuẩn hóa và qua quality gate
├── quarantine/   # bản ghi bị loại kèm nguyên nhân
├── warehouse/    # agriinsight.db và load report
├── gold/         # KPI, alerts và datasets cho dashboard
├── quality/      # báo cáo data quality trước/sau
├── _tmp/         # temp report/build được kiểm soát, ngoài manifest
└── manifest.json # lineage tối thiểu, row count và checksum
```

## Tài liệu

- [Project overview và PDR](docs/project-overview-pdr.md)
- [Kiến trúc MVP](docs/architecture.md)
- [System architecture](docs/system-architecture.md)
- [Codebase summary](docs/codebase-summary.md)
- [Data contracts](docs/data-contracts.md)
- [Code standards](docs/code-standards.md)
- [Deployment guide](docs/deployment-guide.md)
- [Design guidelines](docs/design-guidelines.md)
- [Project roadmap](docs/project-roadmap.md)
- [KPI catalog](docs/kpi-catalog.md)
- [Tiêu chí nghiệm thu](docs/mvp-acceptance.md)
- [Reporting và vận hành local](docs/reporting-and-local-operations.md)

## Big-data demo và visual assets

The standard profile remains the fast local/CI run. For a production-like
demonstration, the named `big-data` profile resolves to 10 farms, 120 fields,
365 sensor days, and 24 readings/day:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-big-data-demo.ps1
```

The guarded run writes to `artifacts/big-data` on drive D, records the resolved
configuration and configuration-fingerprinted `run_id`, and produces 1,050,000
validated sensor facts after intentional Bronze quality fixtures. The latest
verified run passed quality/checksum/warehouse gates with a 388.2 MB artifact
set. Do not commit generated artifacts.

The dashboard now includes six optimized WebP visuals under
[`dashboard/assets/generated/`](dashboard/assets/generated/), with captions and
an explicit **AI-generated demo evidence** warning on Crop Health. The catalog
records dimensions, SHA-256, prompt intent, accessible descriptions, and the
evidence boundary. A 1280 × 640 social-preview source is available at
[`docs/assets/agriinsight-social-preview.jpg`](docs/assets/agriinsight-social-preview.jpg);
GitHub account settings may still require a one-time manual upload.
