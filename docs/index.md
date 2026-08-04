# AgriInsight Documentation

Đây là điểm vào chuẩn cho tài liệu của AgriInsight. Repository là portfolio /
pre-production reference implementation; mọi tuyên bố production phải đi qua
control record riêng, không được suy ra từ CI, container publication hay demo.

## Bắt đầu theo nhu cầu

| Bạn cần | Source of truth |
|---|---|
| Hiểu sản phẩm và yêu cầu | [Project Overview & PDR](project-overview-pdr.md) |
| Hiểu kiến trúc hiện tại | [System Architecture](system-architecture.md) |
| Hiểu analytics MVP chi tiết | [Analytics Plane Architecture](architecture.md) |
| Tra cứu API, Gold và schema invariants | [Data Contracts](data-contracts.md) |
| Thiết lập và release | [Deployment Guide](deployment-guide.md) |
| Phát triển backend | [Backend Development](backend-development.md) |
| Vận hành/recovery backend | [Backend Deployment](backend-deployment.md) |
| Chạy pipeline, reports và disk guard | [Reporting & Local Operations](reporting-and-local-operations.md) |
| Quy tắc code và review | [Code Standards](code-standards.md) |
| UI, responsive và media evidence | [Design Guidelines](design-guidelines.md) |
| Trạng thái và bước tiếp theo | [Project Roadmap](project-roadmap.md) |
| Quyết định GO/NO-GO production | [Production Readiness](production-readiness.md) |

## Ranh giới tài liệu

- `README.md` là landing page: giá trị sản phẩm, quick start và evidence đã chọn.
- `system-architecture.md` là kiến trúc canonical của toàn platform.
- `architecture.md` chỉ mô tả analytics plane; không đại diện toàn hệ thống.
- `deployment-guide.md` mô tả promotion/release mechanics; không cấp quyền GO.
- `production-readiness.md` là control record duy nhất cho external production.
- `plans/` giữ lịch sử thực thi và acceptance evidence; không thay thế evergreen docs.

## Visual evidence

- [Hosted product screenshots](assets/screens/README.md): 14 desktop/mobile
  captures và machine-readable provenance catalog.
- [Visual Media Gallery](media-gallery.md): toàn bộ UI stills, forecast stills,
  GIF motion previews, contextual WebP và diagram được phân loại theo boundary.
- [System architecture SVG](assets/agriinsight-system-architecture.svg) và
  [security boundary SVG](assets/agriinsight-security-boundaries.svg): nguồn
  vector canonical; PNG tương ứng dùng để render ổn định trên GitHub.
- [Contextual AI visuals](../dashboard/assets/generated/README.md): artwork demo
  được cách ly, không phải UI screenshot hay dữ liệu nông nghiệp thật.

## Quy tắc cập nhật

Đọc code trước khi sửa docs, kiểm tra file/link/command thực sự tồn tại và cập
nhật claim theo bằng chứng hiện tại. Không ghi secret, dữ liệu cá nhân, raw token
hoặc approval giả vào repository.
