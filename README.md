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

Backend Java 21/Spring Boot nằm riêng trong `backend/`. Phase 1 được nghiệm thu ngày 2026-07-19; Phase 2 OIDC identity/security boundary được nghiệm thu ngày 2026-07-20. Backend hiện có application foundation, deny-by-default security, external JWT validation, exact identity bootstrap, route inventory và endpoint tối thiểu `GET /api/v1/me`.

Bằng chứng hiện tại:

- Disk guard PASS trước các tác vụ nặng; Maven `verify` đạt 57 unit/security/module test và 1 Testcontainers integration test trên PostgreSQL 18 sạch, gồm Flyway V1-V3 apply + validate.
- OIDC kiểm tra signature/asymmetric algorithm, issuer, API audience, `exp`, `nbf`, subject và access-token discriminator; `(iss, sub)` được resolve chính xác sang profile/tenant đang active mà không tin JWT role/tenant claim.
- Public route chỉ gồm health allowlist; `/api/v1/me` yêu cầu JWT hợp lệ; mapping chưa đăng ký bị deny. Security response/log không chứa raw token hoặc provider diagnostics.
- Local JDK mới hơn biên dịch bằng `--release 21`; multi-stage image dùng Temurin 21, chạy non-root `10001:10001`, chỉ chứa `/app/app.jar` và đã qua smoke test liveness/readiness/fail-closed OIDC.
- Regression analytics đạt 65 test pass, 3 test PDF skip có chủ đích khi thiếu optional report extras; compileall, Node syntax, Compose config và wheel build đều đạt.
- Docker Desktop đã được trả về trạng thái dừng sau kiểm thử; không để lại smoke/Testcontainers container và vẫn giữ upstream `postgres:18.0-alpine`.

Các cổng còn mở thuộc phase sau:

- Phase 2 chỉ đóng identity boundary. Tenant permission enrichment, restricted runtime DB role, transaction-local tenant context, PostgreSQL RLS và provisioning thuộc Phase 3; business CRUD thuộc Phase 4-6. Vì vậy backend chưa production-ready và identity mặc định vẫn tắt.
- Protected Java 21 CI, image scan, SBOM/provenance và publication immutable lên Docker Hub thuộc Phase 7. Image backend hiện chỉ có local tag kiểm thử; chưa push registry.
- PostgreSQL 18 chỉ được lấy từ upstream cho integration test, tuyệt đối không republish dưới namespace AgriInsight.

Xem [báo cáo nghiệm thu Backend Phase 1](./plans/260719-0753-backend-auth-rbac/reports/acceptance-2026-07-19-backend-phase1.md) và [Backend Phase 2](./plans/260719-0753-backend-auth-rbac/reports/acceptance-2026-07-20-backend-phase2.md).

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

Frontend discovery cho Inventory Control có fixture chỉ đọc, cố định phạm vi `WH-001`, đối soát 10 cảnh báo và 15 SKU-location từ Gold/Silver. Source/static/browser/print/review gates đã hoàn tất; đây chưa phải màn hình production và không cho phép receipt, issue, reversal hoặc mutation trước backend Phase 5. Xem [`inventory-control-review.md`](./plans/260719-0753-backend-auth-rbac/design-system/prototypes/inventory-control-review.md).

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
