from __future__ import annotations

import random
from datetime import date, timedelta

import pandas as pd

from agriinsight.config import GenerationConfig


MATERIAL_CATALOG = (
    ("MAT-NPK", "Phân NPK 16-16-8", "Phân bón", "kg", 4_000, 12_000, 540, 18_500),
    ("MAT-UREA", "Phân urê", "Phân bón", "kg", 2_800, 8_500, 540, 15_200),
    ("MAT-ORGANIC", "Phân hữu cơ", "Phân bón", "kg", 5_000, 16_000, 365, 6_800),
    ("MAT-FUNGICIDE", "Thuốc trừ nấm sinh học", "Thuốc BVTV", "liter", 380, 1_100, 730, 128_000),
    ("MAT-INSECT", "Thuốc trừ sâu sinh học", "Thuốc BVTV", "liter", 320, 900, 730, 142_000),
    ("MAT-SEED-RICE", "Hạt giống lúa", "Hạt giống", "kg", 1_200, 3_600, 270, 34_000),
    ("MAT-SEED-VEG", "Hạt giống rau", "Hạt giống", "kg", 180, 550, 240, 215_000),
    ("MAT-COFFEE", "Cây giống cà phê", "Cây giống", "piece", 900, 2_800, 120, 24_000),
    ("MAT-DURIAN", "Cây giống sầu riêng", "Cây giống", "piece", 180, 520, 120, 165_000),
    ("MAT-DRIP", "Dây tưới nhỏ giọt", "Vật tư tưới", "piece", 240, 800, 1_825, 96_000),
    ("MAT-FILTER", "Lõi lọc tưới", "Vật tư tưới", "piece", 80, 260, 1_095, 285_000),
    ("MAT-FUEL", "Nhiên liệu máy nông nghiệp", "Nhiên liệu", "liter", 1_000, 3_200, 365, 24_500),
    ("MAT-PPE", "Bộ bảo hộ lao động", "An toàn", "piece", 90, 280, 1_095, 420_000),
    ("MAT-TRAP", "Bẫy côn trùng", "Kiểm soát dịch hại", "piece", 220, 700, 730, 38_000),
    ("MAT-PH", "Dung dịch cân bằng pH", "Xử lý đất", "liter", 260, 820, 540, 76_000),
    ("MAT-MULCH", "Màng phủ nông nghiệp", "Vật tư canh tác", "kg", 480, 1_500, 1_095, 52_000),
    ("MAT-TOOLS", "Dụng cụ cầm tay", "Công cụ", "piece", 120, 400, 1_825, 310_000),
    ("MAT-PACK", "Bao bì thu hoạch", "Bao bì", "piece", 4_000, 14_000, 1_095, 4_800),
)

SUPPLIER_CATALOG = (
    ("SUP-001", "Nông nghiệp Xanh Việt", "Đắk Lắk", 4.7),
    ("SUP-002", "Vật tư Mekong", "Cần Thơ", 4.5),
    ("SUP-003", "Sinh học Đông Nam", "Đồng Nai", 4.6),
    ("SUP-004", "Giống cây Việt", "Lâm Đồng", 4.4),
    ("SUP-005", "Thiết bị Tưới Việt", "TP. Hồ Chí Minh", 4.3),
    ("SUP-006", "An toàn Nông trại", "Bình Dương", 4.5),
    ("SUP-007", "Bao bì Miền Tây", "Long An", 4.2),
    ("SUP-008", "Cơ khí Cao Nguyên", "Gia Lai", 4.4),
)


def _money(value: float) -> int:
    return int(round(value / 100) * 100)


def generate_inventory(
    config: GenerationConfig, farms: pd.DataFrame
) -> dict[str, pd.DataFrame]:
    """Generate deterministic warehouse master data and inventory movements."""

    rng = random.Random(config.seed + 101)
    materials = pd.DataFrame(
        [
            {
                "material_code": row[0],
                "material_name": row[1],
                "category": row[2],
                "base_unit": row[3],
                "reorder_point": row[4],
                "target_stock_level": row[5],
                "shelf_life_days": row[6],
                "reference_unit_cost_vnd": row[7],
            }
            for row in MATERIAL_CATALOG[: config.material_count]
        ]
    )
    suppliers = pd.DataFrame(
        [
            {
                "supplier_code": row[0],
                "supplier_name": row[1],
                "province": row[2],
                "quality_rating": row[3],
            }
            for row in SUPPLIER_CATALOG
        ]
    )
    warehouses = pd.DataFrame(
        [
            {
                "warehouse_code": f"WH-{index + 1:03d}",
                "farm_code": farm.farm_code,
                "warehouse_name": f"Kho vật tư {farm.farm_name}",
                "capacity_value_vnd": int(float(farm.registered_area_ha) * 48_000_000),
                "temperature_controlled": bool(index % 3 == 0),
            }
            for index, farm in enumerate(farms.itertuples(index=False))
        ]
    )

    month_starts = [timestamp.date() for timestamp in pd.date_range("2025-01-01", config.as_of_date, freq="MS")]
    transactions: list[dict[str, object]] = []
    transaction_number = 0

    def append_transaction(
        *,
        transaction_date: date,
        warehouse_code: str,
        material: pd.Series,
        transaction_type: str,
        quantity: float,
        unit_cost: float,
        supplier_code: str | None,
        expiry_date: date | None,
    ) -> None:
        nonlocal transaction_number
        transaction_number += 1
        transactions.append(
            {
                "transaction_id": f"INV-TX-{transaction_number:07d}",
                "transaction_date": transaction_date.isoformat(),
                "warehouse_code": warehouse_code,
                "material_code": material["material_code"],
                "supplier_code": supplier_code,
                "transaction_type": transaction_type,
                "quantity": round(quantity, 3),
                "unit": material["base_unit"],
                "unit_cost_vnd": _money(unit_cost),
                "total_amount_vnd": _money(quantity * unit_cost),
                "batch_code": (
                    f"BATCH-{transaction_number:07d}" if transaction_type == "IN" else None
                ),
                "expiry_date": expiry_date.isoformat() if expiry_date else None,
            }
        )

    for warehouse_index, warehouse in enumerate(warehouses.itertuples(index=False)):
        for material_index, material in materials.iterrows():
            balance = 0.0
            reference_cost = float(material["reference_unit_cost_vnd"])
            reorder_point = float(material["reorder_point"])
            target_stock = float(material["target_stock_level"])
            shelf_life_days = int(material["shelf_life_days"])

            for month_index, month_start in enumerate(month_starts):
                if month_index % 3 == 0 or balance < reorder_point:
                    receipt_quantity = max(target_stock - balance, reorder_point * 1.4)
                    receipt_quantity *= rng.uniform(0.93, 1.08)
                    receipt_date = month_start + timedelta(days=2 + (material_index % 5))
                    if receipt_date <= config.as_of_date:
                        unit_cost = reference_cost * rng.uniform(0.92, 1.12)
                        append_transaction(
                            transaction_date=receipt_date,
                            warehouse_code=warehouse.warehouse_code,
                            material=material,
                            transaction_type="IN",
                            quantity=receipt_quantity,
                            unit_cost=unit_cost,
                            supplier_code=SUPPLIER_CATALOG[
                                (warehouse_index + material_index + month_index) % len(SUPPLIER_CATALOG)
                            ][0],
                            expiry_date=receipt_date + timedelta(days=shelf_life_days),
                        )
                        balance += receipt_quantity

                usage_quantity = reorder_point * rng.uniform(0.42, 0.68)
                usage_date = month_start + timedelta(days=17 + (warehouse_index % 5))
                if usage_date <= config.as_of_date:
                    append_transaction(
                        transaction_date=usage_date,
                        warehouse_code=warehouse.warehouse_code,
                        material=material,
                        transaction_type="OUT",
                        quantity=usage_quantity,
                        unit_cost=reference_cost,
                        supplier_code=None,
                        expiry_date=None,
                    )
                    balance -= usage_quantity

            status_mode = (warehouse_index * len(materials) + material_index) % 10
            adjustment_date = config.as_of_date - timedelta(days=2 + material_index % 3)
            if status_mode == 0 and balance > reorder_point * 0.4:
                quantity = balance - reorder_point * 0.4
                append_transaction(
                    transaction_date=adjustment_date,
                    warehouse_code=warehouse.warehouse_code,
                    material=material,
                    transaction_type="OUT",
                    quantity=quantity,
                    unit_cost=reference_cost,
                    supplier_code=None,
                    expiry_date=None,
                )
            elif status_mode == 1:
                quantity = max(balance + reorder_point * 0.2, reorder_point * 0.2)
                append_transaction(
                    transaction_date=adjustment_date,
                    warehouse_code=warehouse.warehouse_code,
                    material=material,
                    transaction_type="OUT",
                    quantity=quantity,
                    unit_cost=reference_cost,
                    supplier_code=None,
                    expiry_date=None,
                )
            elif status_mode == 2:
                quantity = max(target_stock * 2.1 - balance, target_stock)
                append_transaction(
                    transaction_date=adjustment_date,
                    warehouse_code=warehouse.warehouse_code,
                    material=material,
                    transaction_type="IN",
                    quantity=quantity,
                    unit_cost=reference_cost * rng.uniform(0.96, 1.06),
                    supplier_code=SUPPLIER_CATALOG[(warehouse_index + material_index) % 8][0],
                    expiry_date=adjustment_date + timedelta(days=shelf_life_days),
                )
            elif status_mode == 3:
                quantity = reorder_point * 0.55
                append_transaction(
                    transaction_date=adjustment_date,
                    warehouse_code=warehouse.warehouse_code,
                    material=material,
                    transaction_type="IN",
                    quantity=quantity,
                    unit_cost=reference_cost,
                    supplier_code=SUPPLIER_CATALOG[(warehouse_index + material_index) % 8][0],
                    expiry_date=config.as_of_date + timedelta(days=15),
                )

    transaction_frame = pd.DataFrame(transactions)
    if not transaction_frame.empty:
        transaction_frame.loc[0, "warehouse_code"] = (
            f" {str(transaction_frame.loc[0, 'warehouse_code']).lower()} "
        )
        kg_receipts = transaction_frame.index[
            (transaction_frame["unit"] == "kg")
            & (transaction_frame["transaction_type"] == "IN")
        ]
        if len(kg_receipts):
            row_index = int(kg_receipts[0])
            transaction_frame.loc[row_index, "quantity"] = (
                float(transaction_frame.loc[row_index, "quantity"]) / 1_000
            )
            transaction_frame.loc[row_index, "unit_cost_vnd"] = (
                float(transaction_frame.loc[row_index, "unit_cost_vnd"]) * 1_000
            )
            transaction_frame.loc[row_index, "unit"] = "tonne"
        transaction_frame = pd.concat(
            [transaction_frame, transaction_frame.iloc[[0]]], ignore_index=True
        )
        invalid = transaction_frame.iloc[[1]].copy()
        invalid.loc[:, "transaction_id"] = "INV-TX-INVALID-NEGATIVE"
        invalid.loc[:, "quantity"] = -100
        invalid.loc[:, "total_amount_vnd"] = -100 * float(invalid.iloc[0]["unit_cost_vnd"])
        transaction_frame = pd.concat([transaction_frame, invalid], ignore_index=True)

    return {
        "warehouses": warehouses,
        "materials": materials,
        "suppliers": suppliers,
        "inventory_transactions": transaction_frame,
    }

