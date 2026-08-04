# Visual Media Gallery

AgriInsight có nhiều loại media với boundary khác nhau. Trang này gom các
asset để portfolio dễ xem nhưng không đánh đồng artwork, UI screenshot và
analytics evidence.

## Media inventory

| Nhóm | Số lượng | Nguồn | Boundary |
|---|---:|---|---|
| Hosted product UI | 14 WebP | Real seven-persona browser gate | UI evidence, không phải production telemetry |
| Product tours | 2 GIF | Derived from the verified hosted catalog | UI preview; same evidence boundary as source stills |
| Forecast evidence | 3 WebP + 2 GIF | Hosted Keycloak/PostgreSQL/Spring/FastAPI/Next gate | Verified documentation/demo evidence |
| Contextual visuals | 8 WebP + 1 GIF | First-party generated demo artwork | Marketing/context only |
| Architecture/security | 8 SVG/PNG | Repository source diagrams | Technical explanation |

## Hosted product UI

Đây là 14 ảnh desktop/mobile được capture từ real integration stack. Provenance
đầy đủ nằm trong [`screens/catalog.json`](assets/screens/catalog.json), còn
quy trình import được mô tả tại [`screens/README.md`](assets/screens/README.md).

<details>
<summary><strong>Overview, Work và Cost</strong></summary>

### Overview

<img src="assets/screens/overview-dashboard-desktop.webp" width="64%" alt="Overview dashboard desktop"> <img src="assets/screens/overview-dashboard-mobile.webp" width="32%" alt="Overview dashboard mobile">

### Work Operations

<img src="assets/screens/work-operations-desktop.webp" width="64%" alt="Work operations desktop"> <img src="assets/screens/work-operations-mobile.webp" width="32%" alt="Work operations mobile">

### Cost Analysis

<img src="assets/screens/cost-analysis-desktop.webp" width="64%" alt="Cost analysis desktop"> <img src="assets/screens/cost-analysis-mobile.webp" width="32%" alt="Cost analysis mobile">

</details>

<details>
<summary><strong>Crop Health, Data Quality, Assistant và Administration</strong></summary>

### Crop Health

<img src="assets/screens/crop-health-desktop.webp" width="64%" alt="Crop health desktop"> <img src="assets/screens/crop-health-mobile.webp" width="32%" alt="Crop health mobile">

### Data Quality

<img src="assets/screens/data-quality-desktop.webp" width="64%" alt="Data quality desktop"> <img src="assets/screens/data-quality-mobile.webp" width="32%" alt="Data quality mobile">

### Assistant

<img src="assets/screens/assistant-evidence-first-desktop.webp" width="64%" alt="Assistant evidence-first desktop"> <img src="assets/screens/assistant-evidence-first-mobile.webp" width="32%" alt="Assistant evidence-first mobile">

### Tenant Administration

<img src="assets/screens/tenant-administration-desktop.webp" width="64%" alt="Tenant administration desktop"> <img src="assets/screens/tenant-administration-mobile.webp" width="32%" alt="Tenant administration mobile">

</details>

## Verified product tours

Hai tour dưới đây được tạo lại bằng
[`scripts/build-portfolio-tour-gifs.ps1`](../scripts/build-portfolio-tour-gifs.ps1)
trực tiếp từ 14 WebP đã khớp SHA-256 trong hosted catalog. Không có mockup,
frame tự vẽ hoặc dữ liệu khách hàng được thêm vào GIF.

<img src="../assets/generated/agriinsight-product-tour-desktop.gif" width="64%" alt="AgriInsight verified desktop product tour"> <img src="../assets/generated/agriinsight-product-tour-mobile.gif" width="32%" alt="AgriInsight verified mobile product tour">

Mỗi tour có 7 frame theo thứ tự Overview, Work, Cost, Crop Health, Data Quality,
Assistant và Administration. Desktop là 960×600; mobile là 390×844.

## Forecast evidence and motion previews

Forecast media stays separate from the general UI catalog because its evidence
contract, run provenance and interpretation boundary are different.

### Inventory demand forecast

<img src="assets/screens/inventory-demand-forecast-desktop.webp" width="64%" alt="Inventory demand forecast desktop evidence">
<img src="../assets/generated/agriinsight-inventory-forecast-loop.gif" width="32%" alt="Inventory demand forecast motion preview">

The still and GIF are paired evidence from the accepted inventory forecast
hosted gate. Details, hashes and non-production boundary:
[`assets/generated/README.md`](../assets/generated/README.md#inventory-demand-forecast-evidence).

### Yield forecast

<img src="assets/screens/yield-forecast-desktop.webp" width="64%" alt="Yield forecast desktop evidence">
<img src="assets/screens/yield-forecast-mobile.webp" width="32%" alt="Yield forecast mobile evidence">
<img src="../assets/generated/agriinsight-yield-forecast-loop.gif" width="49%" alt="Yield forecast motion preview">

The yield stills and GIF are accepted internal decision-support evidence, not
agronomic ground truth, model accuracy/SLA, or external operational approval.

### Field-ledger contextual loop

<img src="../assets/generated/agriinsight-field-ledger-loop.gif" width="49%" alt="Field ledger contextual demo loop">

This is a first-party contextual demo loop derived from the reviewed field
scene. It is not a real field observation and is intentionally separated from
the hosted product screenshot catalog.

## Contextual visual system

These eight WebP assets are intentionally labeled artwork for product context.
They are useful for the project narrative, but they must never be cited as UI
behavior, agronomic data, or production evidence.

<details>
<summary><strong>Open contextual visuals</strong></summary>

<img src="../dashboard/assets/generated/overview-fields.webp" width="24%" alt="Contextual overview fields artwork"> <img src="../dashboard/assets/generated/farm-performance.webp" width="24%" alt="Contextual farm performance artwork"> <img src="../dashboard/assets/generated/inventory-control.webp" width="24%" alt="Contextual inventory control artwork"> <img src="../dashboard/assets/generated/work-operations.webp" width="24%" alt="Contextual work operations artwork">

<img src="../dashboard/assets/generated/cost-procurement.webp" width="24%" alt="Contextual cost procurement artwork"> <img src="../dashboard/assets/generated/crop-health-evidence.webp" width="24%" alt="Contextual crop health artwork"> <img src="../dashboard/assets/generated/data-quality-sensors.webp" width="24%" alt="Contextual data quality artwork"> <img src="../dashboard/assets/generated/tenant-administration.webp" width="24%" alt="Contextual tenant administration artwork">

Full source and labeling policy: [`dashboard/assets/generated/README.md`](../dashboard/assets/generated/README.md).

</details>

## Architecture and trust diagrams

<img src="assets/agriinsight-system-architecture.png" width="64%" alt="AgriInsight system architecture diagram"> <img src="assets/agriinsight-security-boundaries.png" width="32%" alt="AgriInsight security boundaries diagram">

Forecast-specific delivery diagrams are maintained beside the canonical system
source: [inventory demand architecture](assets/inventory-demand-forecast-architecture.png)
and [yield forecast architecture](assets/yield-forecast-architecture.png).

## Maintenance rules

- Keep hosted UI screenshots tied to `catalog.json` and a passing CI artifact.
- Keep GIFs paired with their source stills, run URL, hash and boundary note.
- Keep contextual artwork out of product claims and agronomic conclusions.
- Review media at README width before merging; broken framing blocks publication.
