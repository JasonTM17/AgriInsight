# Reporting và vận hành local

## Phạm vi

Cost Analysis report là bề mặt local/internal trên Gold đã validate. Nó xuất ba
format từ một normalized request, không cho chọn path tùy ý và không kết hợp
operating cost, procurement spend hoặc inventory value thành một “total cost”.

Dashboard chưa có authentication, RBAC hoặc row-level authorization. Compose chỉ
publish `127.0.0.1:8501`; không đổi sang interface public cho đến khi security
milestone hoàn tất.

## Chuẩn bị runtime

```powershell
python -m pip install -e ".[dev,dashboard,reports]"
python -m agriinsight run --output artifacts
streamlit run dashboard/app.py
```

`reports` cung cấp ReportLab cho PDF; Noto Sans và SIL OFL được đóng gói trong
wheel. CSV/PDF không phụ thuộc XLSX. XLSX chỉ bật khi cả hai biến sau trỏ tới
runtime `@oai/artifact-tool` hợp lệ:

```powershell
$env:AGRIINSIGHT_NODE_EXECUTABLE = "C:\Program Files\nodejs\node.exe"
$env:AGRIINSIGHT_NODE_MODULES = "D:\runtime\node_modules"
```

Thiếu hoặc sai một biến làm nút XLSX disabled với thông báo capability ổn định;
CSV/PDF vẫn hoạt động. Chi tiết runtime chỉ ghi vào server log, không đưa path hay
stderr lên UI. Không commit runtime path máy cá nhân hoặc secret vào `.env`.

### Big-data profile and visual assets

The regular command is the quick local/CI dataset. Use the guarded runner for
the larger demonstration:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-big-data-demo.ps1
```

It writes `artifacts/big-data` on D, keeps C/D PASS, and records a resolved
configuration fingerprint in `manifest.json`. The verified run passed with
1,050,000 warehouse sensor facts and a 388.2 MB artifact set. The dashboard
visual catalog lives in `dashboard/assets/generated/`; it is application UI
content, not a Gold fact or a registry image. Crop Health imagery is explicitly
AI-generated demo evidence and cannot support a real agronomic diagnosis.

## Luồng export

1. Mở `Cost Analysis`.
2. Chọn operating hoặc procurement lens.
3. Chọn filter. `Tất cả` bị loại khỏi raw mapping; tháng đơn được normalize
   thành cùng `month_from` và `month_to`.
4. Submit `Áp dụng và tạo report`. Bundle cũ của lens đó bị xóa trước khi build.
5. Tải CSV/PDF và XLSX khi capability tồn tại. Filename chứa ngày dữ liệu,
   scope và filter hash; CSV mang lineage từ manifest.

Giới hạn fail-closed: 25.000 dòng cho từng detail table và toàn bundle, 10 MiB
cho tổng bytes. Empty result, unknown/path-like filter, sai semantic lens hoặc
manifest không hợp lệ đều không sinh download.

## Disk guard

Chạy trước và sau pipeline/export/build lớn:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/check-workspace-disk.ps1
```

| Drive | WARN, exit 0 | FAIL, exit non-zero |
|---|---:|---:|
| C | dưới 10 GB | dưới 8 GB |
| D | dưới 25 GB | dưới 20 GB |

Thiếu hoặc không đọc được drive là FAIL. Script chỉ quan sát và in evidence;
không xóa file, cache hoặc artifact. Khi WARN, dừng build/cài đặt nặng và giữ
temp/cache trong `artifacts/_tmp` trên D.

Evidence sau big-data/Python UI gates gần nhất: C còn 10.274 GB và D còn
25.364 GB, cả hai PASS. Vì C gần ngưỡng cảnh báo, mọi Maven/temp/cache nặng
phải giữ trên D.

## Backend verification

Dùng entry point được guard từ repository root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify
```

Runner chỉ bắt đầu Maven khi disk guard trả `DISK_GUARD overall=PASS`. Maven repository, temp và user-home phải resolve vào ổ D; dùng `MAVEN_REPO_LOCAL`, `MAVEN_TEMP_DIR`, `MAVEN_USER_HOME` nếu cần override. `MAVEN_ARGS`, `MAVEN_CONFIG`, `MAVEN_PROJECTBASEDIR`, và project `.mvn/maven.config` phải unset/không tồn tại; không truyền trực tiếp `-Dmaven.repo.local` hoặc `-Djava.io.tmpdir`.
Với goal `verify`, runner kiểm tra Docker daemon trước Maven và từ chối `skipTests`, `skipIT`, `fail-never`, POM/settings/module selector thay thế, để integration gate không thể biến thành false green.

Runtime DB connections also carry bounded PostgreSQL `connectTimeout`, `loginTimeout`, and `socketTimeout` values so a black-holed host cannot turn a readiness probe into an unbounded socket wait.

| Backend gate | Trạng thái hiện tại |
|---|---|
| Fresh Maven verification | PASS: 487 Surefire + 92 Failsafe; zero failures/errors/skips |
| Docker daemon | Available during guarded verification |
| Testcontainers + Flyway PostgreSQL | PASS; không còn container test treo sau gate |
| Java 21 CI | Chưa verify |
| Compose + backend image build | Local non-root smoke đã verify; Phase 7 sẽ rebuild/scan image sau Phase 6 |
| Docker Hub publish + image verification | Chưa claim |

Không đổi blocked gate thành PASS bằng cách skip integration test. Chỉ push image first-party của AgriInsight sau khi test, review và release hardening đạt; không republish PostgreSQL hoặc image upstream.

## Artifact và rollback

- Dashboard dùng `artifacts/_tmp/cost-reports`; XLSX adapter tạo child temp và
  dọn cả success/failure.
- Dashboard đọc `manifest.json` trước/sau, hash đúng bytes của 9 Cost Gold CSV,
  so khớp checksum rồi mới parse. Manifest chuyển generation được retry một lần;
  snapshot hỏng hoặc không ổn định fail closed với thông báo không chứa path.
- `_tmp` nằm ngoài manifest checksum. Pipeline không để report làm thay đổi
  reproducibility của Gold.
- Khi Cost Gold hoặc manifest thiếu, chỉ route Cost Analysis báo lệnh regenerate;
  năm dashboard cũ vẫn hoạt động.
- Rollback UI: gỡ route Cost Analysis và các page modules. Gold/report service
  vẫn độc lập, không cần rollback warehouse schema.

## Validation

```powershell
python -m pytest
python -m compileall -q -f src dashboard tests
node --check src/agriinsight/report-assets/build-cost-report.mjs
docker compose -f compose.yaml config --quiet
python -m pip wheel . --no-deps --no-build-isolation `
  --wheel-dir artifacts/_tmp/wheel
```

Docker image cài `dashboard,reports`; Compose publish dashboard trên loopback,
mount `artifacts/` read-only và overlay riêng `artifacts/_tmp` writable cho report
temp. `docker compose config` không cần daemon; build/up cần Docker Desktop.
