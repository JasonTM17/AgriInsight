# KPI catalog

## Executive

| KPI | Định nghĩa |
|---|---|
| Total revenue | Tổng `fact_harvest.revenue_vnd` |
| Total cost | Tổng `fact_crop_activity.total_cost_vnd` |
| Profit | Revenue − Cost |
| Profit margin | Profit / Revenue × 100 |
| Yield | Tổng sản lượng thu hoạch theo kg |
| Risk alerts | Season alerts + Inventory critical/warning alerts + Crop Health high-risk fields |

## Inventory

| KPI | Định nghĩa |
|---|---|
| Stock quantity | `IN quantity − OUT quantity` theo warehouse/material |
| Inventory value | `max(stock, 0) × weighted average inbound unit cost` |
| Average daily usage | Tổng xuất 30 ngày / 30 |
| Days of supply | Stock / average daily usage |
| Predicted 30-day need | Average daily usage × 30 |
| Recommended order | `max(target stock − current stock, 0)` |
| Low stock | `0 < stock ≤ reorder point` |
| Stockout | `stock ≤ 0` |
| Overstock | `stock > 150% × target stock` |

ABC dùng tỷ trọng giá trị tồn kho toàn doanh nghiệp:

- A: các SKU tạo nên khoảng 80% giá trị đầu tiên.
- B: phần tiếp theo đến khoảng 95%.
- C: phần giá trị còn lại.

Expiry alert hiện dùng ngày hết hạn gần nhất của inbound batch còn trong lịch sử. Batch-level FIFO/FEFO depletion sẽ được bổ sung khi backend inventory có allocation ledger.

## Crop Health

Risk score `0..100` cộng điểm từ:

- Độ ẩm đất ngoài vùng tốt: tối đa 35 điểm.
- Nhiệt độ cực đoan: 20 điểm.
- pH ngoài `5.0..7.5`: 20 điểm.
- Mưa 7 ngày quá thấp/cao: 10–15 điểm.
- Diện tích sâu bệnh và tỷ lệ cây chết: tối đa 55 điểm.
- Sensor không cập nhật quá 2 ngày: 40 điểm.

Phân loại:

- `healthy`: dưới 25.
- `watch`: 25–49.
- `high`: từ 50.

Khuyến nghị ưu tiên sensor offline, sau đó đến sâu bệnh, độ ẩm và pH. Đây là rule-based prescriptive analytics; chưa phải chẩn đoán nông học hoặc mô hình ML.

