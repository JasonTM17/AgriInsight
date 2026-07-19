# Inventory page override

Primary job: prevent stockout and expiry while preserving warehouse and supplier scope.

- Lead with warehouse scope, stock health, FEFO risk, days-of-supply, and ABC segments; use alert text plus icon, not color alone.
- Tables expose SKU/material code, base unit, lot/expiry, quantity, supplier, and last movement; never infer procurement spend as operating cost.
- Receipts/issues/reversals require visible confirmation, version conflict recovery, and audit context.
- Mobile: filter sheet plus priority cards; transaction detail opens a labelled sheet with a back path.
