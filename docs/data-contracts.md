# Data contracts

## Không gian version

| Không gian | Giá trị hiện tại | Phạm vi |
|---|---|---|
| Analytics contract | `agriinsight-bronze-silver-gold-v1` | Bronze, Silver, quarantine, warehouse, Gold và report |
| HTTP API prefix | `/api/v1` | Operational API của backend |
| Flyway schema history | `11` | Tenant anchor, identity/RBAC, farm/workforce/activity/harvest schema và lifecycle guards |

Ba version space độc lập. Không suy ra analytics contract từ HTTP/Flyway version và ngược lại.

## Backend farm/master-data HTTP contract

Phase 4 đã nghiệm thu; contract xác thực bao phủ farm, field, crop, season, Employee, assignment, activity log và harvest:

| Method | Path | Permission | Contract chính |
|---|---|---|---|
| **GET** | `/api/v1/farms` | FARM_READ | `limit=1..100`, `offset=0..10000`, optional `active`/`search`, stable page order |
| **GET** | `/api/v1/farms/{id}` | FARM_READ | assignment-aware visibility, strong `ETag` |
| **POST** | `/api/v1/farms` | FARM_MANAGE | tenant-wide scope, `Idempotency-Key`, `201` + Location |
| **PATCH** | `/api/v1/farms/{id}` | FARM_MANAGE | assigned or tenant-wide scope, `Idempotency-Key` + strong `If-Match` |
| **POST** | `/api/v1/farms/{id}/deactivate` | FARM_MANAGE | tenant-wide scope, reason, idempotency and optimistic version |
| **POST** | `/api/v1/farms/{id}/reactivate` | FARM_MANAGE | tenant-wide scope, reason, idempotency and optimistic version |

| Method | Path | Permission | Contract chính |
|---|---|---|---|
| **GET** | `/api/v1/fields`, `/api/v1/fields/{id}` | FARM_READ | bounded filters, assignment-aware visibility, strong `ETag` cho detail |
| **POST** | `/api/v1/farms/{farmId}/fields` | FARM_MANAGE | parent farm phải visible/active; `Idempotency-Key`; `201` + Location |
| **PATCH** | `/api/v1/fields/{id}` | FARM_MANAGE | FARM-scoped hoặc tenant-wide; strong `If-Match` |
| **POST** | `/api/v1/fields/{id}/deactivate|reactivate` | FARM_MANAGE | idempotent lifecycle; live season chặn deactivate |
| **GET** | `/api/v1/crops`, `/api/v1/crops/{id}` | FARM_READ | tenant catalog, bounded filters, strong `ETag` cho detail |
| **POST/PATCH** | `/api/v1/crops`, `/api/v1/crops/{id}` | FARM_MANAGE | tenant-wide catalog command; idempotency + optimistic locking |
| **POST** | `/api/v1/crops/{id}/deactivate|reactivate` | FARM_MANAGE | idempotent lifecycle; live season chặn deactivate |
| **GET** | `/api/v1/seasons`, `/api/v1/seasons/{id}` | SEASON_READ | bounded farm/field/crop/status filters, farm-assignment visibility |
| **POST/PATCH** | `/api/v1/seasons`, `/api/v1/seasons/{id}` | SEASON_MANAGE | visible active parents; idempotency + strong `If-Match` |
| **POST** | `/api/v1/seasons/{id}/transition` | SEASON_MANAGE | explicit state transition, reason, idempotency + optimistic locking |

| Method | Path | Permission | Contract chính |
|---|---|---|---|
| **GET** | `/api/v1/employees` | WORKFORCE_MANAGE | tenant-wide full master list; bounded active/search filters |
| **GET** | `/api/v1/employees/{id}` | WORKFORCE_MANAGE | full employee master, strong `ETag` |
| **GET** | `/api/v1/employees/eligible` | WORKFORCE_PICKER_READ | active-only redacted `id/code/displayName/active`; no job title/version |
| **POST/PATCH** | `/api/v1/employees`, `/api/v1/employees/{id}` | WORKFORCE_MANAGE | canonical master command, idempotency, strong version for update |
| **POST** | `/api/v1/employees/{id}/deactivate|reactivate` | WORKFORCE_MANAGE | reason, idempotency, optimistic version; live responsibility blocks deactivate |
| **POST** | `/api/v1/farm-assignments` | FARM_ASSIGNMENT_MANAGE | tenant-wide active profile/farm targets, version-zero grant, append-preserved history |
| **POST** | `/api/v1/farm-assignments/{id}/revoke` | FARM_ASSIGNMENT_MANAGE | tenant-wide audited one-way revoke, strong `If-Match` |

| Method | Path | Permission | Contract chính |
|---|---|---|---|
| **GET** | `/api/v1/activities`, `/api/v1/activities/{id}` | ACTIVITY_READ | tenant/assigned-farm/assigned-task scope, bounded filters, strong `ETag` cho detail |
| **POST/PATCH** | `/api/v1/activities`, `/api/v1/activities/{id}` | ACTIVITY_MANAGE | tenant hoặc assigned-farm manager; idempotency, optimistic version, immutable hierarchy |
| **POST** | `/api/v1/activities/{id}/transition` | ACTIVITY_MANAGE | explicit task state machine |
| **POST** | `/api/v1/activities/{id}/assignments` | ACTIVITY_MANAGE | active employee target; append-preserved grant history |
| **POST** | `/api/v1/activities/{id}/assignments/{assignmentId}/revoke` | ACTIVITY_MANAGE | one-way audited revoke |
| **POST** | `/api/v1/activities/{id}/logs` | ACTIVITY_LOG_APPEND | manager hoặc assigned worker; immutable evidence row |
| **POST** | `/api/v1/activities/{id}/logs/{logId}/corrections` | ACTIVITY_LOG_APPEND | manager hoặc worker correcting own lineage; URI metadata không được fetch |
| **GET** | `/api/v1/harvests`, `/api/v1/harvests/{id}` | HARVEST_READ | tenant hoặc assigned-farm scope; bounded hierarchy/date filters |
| **POST** | `/api/v1/harvests` | HARVEST_MANAGE | tenant/assigned-farm manager; KG/TONNE chuẩn hóa về kg trước fingerprint |
| **POST** | `/api/v1/harvests/{id}/corrections` | HARVEST_MANAGE | linked REPLACE/VOID correction; không update/delete fact cũ |

FarmCreateRequest yêu cầu `code`, `displayName`; `reasonCode` optional. FarmUpdateRequest yêu cầu ít nhất một trong `code` hoặc `displayName`. Lifecycle yêu cầu `reasonCode`. Code/reason được canonical hóa trước validation và fingerprint; unknown JSON fields bị từ chối.

FarmResponse chỉ gồm `id`, `code`, `displayName`, `active`, `version`; không trả `tenantId`. Page response gồm `items`, `limit`, `offset`, `hasMore`. Không có hard-delete route.

Deactivate bị chặn khi farm còn field active, season PLANNED/ACTIVE, activity PLANNED/STARTED, hoặc assignment chưa revoked. Application transaction dùng explicit READ_COMMITTED; V7 khóa farm cha từ cả parent-deactivation và live-child write để serialize hai thứ tự cạnh tranh. V8 áp dụng cùng nguyên tắc cho field/crop/season. V9 khóa Employee khi gán trách nhiệm/hoạt động và chặn deactivate khi quan hệ còn sống. V10 khóa profile cho farm assignment và serialize cả grant-first lẫn profile-deactivation-first. V11 serialize live activity với season transition. Các migration fail closed trên dữ liệu nâng cấp bất nhất và giữ ENABLE/FORCE RLS khi rollback. FARM-scoped write khóa active assignment tới commit để revoke không chen giữa authorization và mutation.

## Operational identifiers

- `tenants.id` là UUID ổn định cho operational API.
- `tenants.code` được strip Unicode edge whitespace, uppercase bằng `Locale.ROOT`, rồi phải match `^[A-Z0-9][A-Z0-9._-]{0,63}$`.
- Canonical business code là mapping key xuyên hệ thống đi cùng UUID; không thay UUID bằng code.
- `display_name` nonblank, tối đa 200 ký tự; `version` là optimistic lock không âm.
- Timestamp operational lưu dưới dạng timezone-aware UTC.

## Contract theo layer

| Domain | Bronze | Silver | Gold |
|---|---|---|---|
| Farm operations | farms, fields, crops, seasons, activities, harvests | khóa chuẩn, kg, chi phí không âm | executive, monthly financials, farm performance, crop profitability |
| Cost analysis | activities, harvests, inventory transactions | chi phí hoạt động và giao dịch mua đã chuẩn hóa | cost summary/month/farm/season/activity/detail, procurement summary/detail, reconciliation |
| Inventory | warehouses, materials, suppliers, inventory transactions | đơn vị cơ sở, quan hệ supplier/SKU/location hợp lệ | inventory status, ABC, movements, alerts, 30-day need |
| Crop health | sensors, readings, weather, pest types, observations | range hợp lệ, timestamp và field relation hợp lệ | field risk status, environment daily, pest weekly, alerts |
| Data quality | dữ liệu lỗi có chủ ý | records đã qua gate | report before/after và remediation counts |

## Grain và khóa

| Fact | Grain | Business key |
|---|---|---|
| `fact_crop_activity` | một hoạt động chăm sóc | `activity_id` |
| `fact_harvest` | một lần thu hoạch của mùa vụ | `harvest_id` |
| `fact_inventory_transaction` | một giao dịch SKU tại kho | `transaction_id` |
| `fact_sensor_reading` | một reading của sensor tại timestamp | `reading_id` |
| `fact_weather_daily` | một bản ghi thời tiết trang trại/ngày | `weather_id` |
| `fact_crop_health_observation` | một quan sát hiện trường | `observation_id` |

## Quy tắc bắt buộc

- Business code được trim và uppercase.
- Primary key phải duy nhất và không null.
- Foreign key phải tồn tại trong Silver dimensions.
- Chi phí, số lượng và doanh thu không âm.
- Inventory `IN` phải có supplier và expiry date hợp lệ.
- Đơn vị `tonne` chỉ được đổi sang `kg` khi SKU có base unit là `kg`; đồng thời đổi đơn giá về giá/kg.
- Sensor ranges: nhiệt độ `-10..60°C`, độ ẩm `0..100%`, pH `0..14`, mưa `0..1000 mm`, pin `0..100%`.
- Health percentages nằm trong `0..100%`; severity thuộc `none/low/medium/high`.

## Gold Cost Analysis

| Dataset | Grain | Measure chính |
|---|---|---|
| `cost_summary` | một dòng toàn doanh nghiệp | chi phí hoạt động, doanh thu, lợi nhuận, ngân sách |
| `cost_monthly` | một tháng | chi phí hoạt động và doanh thu theo tháng |
| `cost_farm` | một trang trại | chi phí, doanh thu, sản lượng và ngân sách theo trang trại |
| `cost_season` | một mùa vụ | chi phí, doanh thu, sản lượng, cost/ha, cost/kg và chênh lệch ngân sách |
| `cost_activity` | trang trại + mùa vụ + khu vực + cây trồng + loại hoạt động | tổng chi phí theo nhóm hoạt động |
| `cost_activity_detail` | một `activity_id` | chi tiết vật tư, lao động và chi phí hoạt động |
| `procurement_summary` | farm (xác định qua kho) + nhà cung cấp + kho + vật tư | số lượng và giá trị mua vào |
| `procurement_detail` | một giao dịch `IN` | chi tiết giao dịch mua vào |
| `cost_reconciliation` | một mùa vụ | đối soát tổng chi phí với vật tư + lao động và ngân sách |

Các cột chi phí hoạt động dùng tiền tố `operating_`; giao dịch mua dùng tiền tố
`procurement_`. `fact_crop_activity.total_cost_vnd` là chi phí hoạt động P&L.
`fact_inventory_transaction.total_amount_vnd` của giao dịch `IN` là procurement
spend. `inventory_value_vnd` là giá trị tồn kho tại thời điểm chốt. Ba measure này
không được cộng với nhau. Chưa có allocation ledger nối giao dịch outbound với
`activity_id`, vì vậy hệ thống chưa tuyên bố COGS theo hoạt động hoặc mùa vụ.

Mỗi Gold frame được kiểm tra exact key set, thứ tự cột, logical pandas type và
giá trị số hữu hạn trước khi pipeline ghi CSV; contract drift làm pipeline fail closed.

## Export contract

| Hạng mục | Quy tắc |
|---|---|
| Request allowlist | Chỉ nhận `scope`, `farm`, `crop`, `season`, `activity`, `supplier`, `month_from`, `month_to`, `top_n` |
| Scope rules | `season` chỉ dùng cho operating; procurement không nhận crop/season/activity; all chỉ nhận farm/month |
| Rejection rules | Reject unknown key/value, path-like input, domain-invalid value, sai semantic lens, month sai format/không tồn tại/đảo chiều, result rỗng, hoặc detail rows vượt 25,000 |
| Row limits | Mỗi detail table và tổng hai detail table đều tối đa 25,000 rows; preflight chạy trước sort và render |
| CSV | UTF-8 BOM, safe deterministic filename/hash, formula-like text được escape, mỗi dòng mang `export_version`, `run_id`, `as_of_date`, `source_pipeline`, `filter_hash` |
| PDF | A4 landscape tiếng Việt, Noto Sans/OFL đóng gói, footer + page numbers + filters + run ID + top-N + checks |
| XLSX | Chỉ khả dụng khi có hai biến môi trường explicit cho Node executable và node_modules path; đúng 6 sheet: Summary, Monthly, Cost Detail, Procurement Detail, Checks, Metadata |
| XLSX QA | Formula/native chart, inspect/error scan/render cho cả 6 sheet, `MODEL STATUS: PASS`, formula escaping |
| Bundle cap | In-memory bundle không vượt 10 MB |
| Temp | Chỉ dùng caller-provided directory dưới `artifacts/_tmp`; child temp phải được dọn khi success hoặc failure và không tham gia pipeline manifest checksums |
| Fallback | XLSX unavailable không được chặn CSV/PDF; service trả typed error riêng cho contract lỗi và capability/runtime lỗi |

## Dashboard Cost Analysis contract

- Route yêu cầu đủ 9 frame trong Gold Cost contract và `manifest.json`; thiếu file trả một lỗi regeneration có danh sách đầy đủ.
- Route chỉ nhận snapshot khi manifest trước/sau giống nhau và SHA-256 của đúng CSV
  bytes đã parse khớp checksum; transition được retry một lần rồi fail closed.
- UI “Tháng” là một tháng đơn, được normalize thành `month_from = month_to` trong export request.
- Form operating và procurement, request/bundle/session key, table và download control tách riêng; procurement spend không đi vào operating KPI.
- Activity/month không có budget, area hoặc harvest allocation riêng. Vì vậy operating total theo đúng detail đã lọc, còn budget variance/cost per ha/cost per kg được ghi rõ là ngữ cảnh mùa vụ đã chọn.
- Bundle download chỉ tồn tại sau submit và phải khớp normalized request cùng manifest run/date/pipeline hiện hành.

## Versioning

Analytics contract hiện tại là `agriinsight-bronze-silver-gold-v1`. Thay đổi breaking phải tạo version mới, migration warehouse tương ứng và regression test cho Gold consumers.

Backend operational API dùng `/api/v1`; Flyway migration number là backend schema history. Hai giá trị này không đổi analytics version.
