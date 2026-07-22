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

Expiry alert trong Gold vẫn dùng ngày hết hạn gần nhất của inbound batch còn
trong lịch sử. Backend Phase 5 now owns batch-level FEFO depletion, allocation,
and reversal truth in PostgreSQL; a future versioned ETL may consume it without
changing the current Gold contract.

## Cost Analysis

| KPI | Định nghĩa |
|---|---|
| Operating material cost | Tổng `fact_crop_activity.material_cost_vnd` |
| Operating labor cost | Tổng `fact_crop_activity.labor_cost_vnd` |
| Operating total cost | Tổng `fact_crop_activity.total_cost_vnd`; phải bằng material + labor |
| Operating profit | `fact_harvest.revenue_vnd − operating total cost` |
| Operating profit margin | `operating profit / revenue × 100`; bằng `0` khi doanh thu bằng `0` |
| Operating cost/ha | Operating total cost / tổng diện tích mùa vụ vận hành |
| Operating cost/kg | Operating total cost / sản lượng thu hoạch; bằng `0` khi chưa có sản lượng |
| Budget variance | Operating total cost − `dim_season.budget_cost_vnd`; số dương là vượt ngân sách |
| Procurement spend | Chỉ tổng `fact_inventory_transaction.total_amount_vnd` khi `transaction_type = 'IN'` |

Procurement spend không phải operating expense và inventory value không phải chi
phí. Dashboard/report phải hiển thị chúng thành các lens độc lập cho đến khi có
consumption allocation ledger đủ để tính COGS.

Trong Cost Analysis dashboard, filter farm/crop/season quyết định ngữ cảnh mùa vụ
cho budget variance, cost/ha và cost/kg. Filter activity/month tiếp tục lọc
operating detail và operating total, nhưng không giả lập budget/area/harvest
allocation chưa tồn tại; UI ghi chú rõ ngữ cảnh rộng hơn. Procurement panel chỉ
đọc giao dịch `IN`, group theo farm/supplier/warehouse/material business code kèm
tên hiển thị và không cộng vào các KPI P&L.

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
