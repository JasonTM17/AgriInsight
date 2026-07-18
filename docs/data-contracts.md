# Data contracts

## Contract theo layer

| Domain | Bronze | Silver | Gold |
|---|---|---|---|
| Farm operations | farms, fields, crops, seasons, activities, harvests | khóa chuẩn, kg, chi phí không âm | executive, monthly financials, farm performance, crop profitability |
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

## Versioning

Contract hiện tại là `agriinsight-bronze-silver-gold-v1`. Thay đổi breaking phải tạo version mới, migration warehouse tương ứng và regression test cho Gold consumers.

