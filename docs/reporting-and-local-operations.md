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
visual catalog lives in `dashboard/assets/generated/`; it is 8 contextual AI
demo visuals, not Gold facts or registry images. The 14 hosted CI screenshots
live separately under `docs/assets/screens/` and trace back to Actions run
`30885890858`. Crop Health imagery is explicitly AI-generated demo evidence and
cannot support a real agronomic diagnosis.

## Realtime E2E

Use `scripts/run-realtime-e2e-tests.ps1` for the outbox-to-Kafka gate. Local
mode writes runtime under `artifacts/_tmp/realtime-e2e` on D and only starts
after `DISK_GUARD overall=PASS`. It verifies owned Testcontainers cleanup and
will not delete unrelated Docker resources.

The realtime acceptance target is per-run `p95 <= 30s` across 20 accepted
outbox-to-authenticated-summary samples. The test logs `freshness_p95_millis`;
an individual green source run is not a production latency claim.

Hosted mode uses `-HostedCi`, requires the GitHub-hosted Linux markers and
`RUNNER_TEMP`, and keeps the runtime under the hosted runner temp path. Hosted
workflow [`30337950699`](https://github.com/JasonTM17/AgriInsight/actions/runs/30337950699)
passed job [`90207600976`](https://github.com/JasonTM17/AgriInsight/actions/runs/30337950699/job/90207600976)
with 20 samples, `freshness_p95_millis=130`, and `recovery_millis=5094`. This
is internal acceptance evidence only; it is not a production latency promise or release approval.

If C or D is below the documented floor, keep local realtime Docker work off
the workstation and wait for hosted CI or restored headroom. WARN is not a
safe state for realtime verification.

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
temp/cache trong `artifacts/_tmp` trên D. Realtime/backend Docker work cũng yêu
cầu `DISK_GUARD overall=PASS`; WARN exit `0` không đủ an toàn cho local
verification.

Ngưỡng trong bảng là mặc định và có thể override từng ổ qua environment:
`AGRIINSIGHT_DISK_GUARD_C_WARN_GB`, `AGRIINSIGHT_DISK_GUARD_C_FAIL_GB`,
`AGRIINSIGHT_DISK_GUARD_D_WARN_GB`, `AGRIINSIGHT_DISK_GUARD_D_FAIL_GB`. Giá trị
không phải số, WARN thấp hơn FAIL, hoặc bất kỳ ngưỡng dưới sàn tuyệt đối 8 GB
đều là configuration FAIL (exit 2) chứ không âm thầm bỏ qua. Mọi dòng
`DISK_GUARD drive=...` luôn in `warn_below_gb`, `fail_below_gb` và
`policy=default|override`, nên một run đã hạ ngưỡng không thể bị đọc lẫn thành
run mặc định. Override chỉ để chạy được trong workspace chật; nó không nới bất
kỳ gate chất lượng nào.

Docker Desktop image/cache vẫn có thể làm tăng áp lực lên C ngay cả khi project
runtime/log nằm ở D. Khi disk guard đang WARN hoặc FAIL, chuyển verification
nặng sang hosted CI thay vì cố chạy local.

## Backend verification

Dùng entry point được guard từ repository root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify
```

Runner chỉ bắt đầu Maven khi disk guard trả `DISK_GUARD overall=PASS`. Maven repository, temp và user-home phải resolve vào ổ D; dùng `MAVEN_REPO_LOCAL`, `MAVEN_TEMP_DIR`, `MAVEN_USER_HOME` nếu cần override. `MAVEN_ARGS`, `MAVEN_CONFIG`, `MAVEN_PROJECTBASEDIR`, và project `.mvn/maven.config` phải unset/không tồn tại; không truyền trực tiếp `-Dmaven.repo.local` hoặc `-Djava.io.tmpdir`.
Với goal `verify`, runner kiểm tra Docker daemon trước Maven và từ chối `skipTests`, `skipIT`, `fail-never`, POM/settings/module selector thay thế, để integration gate không thể biến thành false green.

Runtime DB connections also carry bounded PostgreSQL `connectTimeout`, `loginTimeout`, and `socketTimeout` values so a black-holed host cannot turn a readiness probe into an unbounded socket wait.
The merged alert-worker hardening uses the same backend gate shape: 600 main + 302 test sources compiled and 42 focused tests passed locally. Because D remained below the heavy-work warning floor, hosted main CI `30413064146` supplied the PostgreSQL/Kafka, browser, and candidate-image gates; protected release run `30413877863` supplied exact-digest publication evidence.

| Backend gate | Trạng thái hiện tại |
|---|---|
| Local guarded verify | Chỉ chạy khi `DISK_GUARD overall=PASS`; WARN không đủ |
| Docker daemon | Required cho `verify`, realtime Compose và image build |
| Testcontainers + Flyway PostgreSQL | Historical Phase 7 evidence có PASS; realtime internal acceptance passed real PostgreSQL/Kafka in hosted job `90207600976` |
| Java 21 CI | Dùng hosted GitHub Actions làm source of truth khi local disk không đủ an toàn |
| Compose + backend image build | Temurin 21.0.11 JRE Noble, UID/GID 10001; không tự coi image build local là release evidence |
| Registry image verification | Chỉ semantic-tag protected workflow mới đủ thẩm quyền publish/verify Docker Hub + GHCR |

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
