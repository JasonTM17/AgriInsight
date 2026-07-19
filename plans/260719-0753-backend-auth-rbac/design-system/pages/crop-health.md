# Crop health page override

Primary job: turn sensor/weather/observation evidence into a bounded field-risk decision.

- Pair a field risk list with time-series context and an evidence lineage drawer; label thresholds and units directly.
- Use accessible patterns/line styles for risk categories and provide a textual alert summary beside charts.
- Never imply realtime or ML confidence until the backend contract says so; stale sensor data is an explicit state.
- Mobile: prioritize the highest-risk field and recent evidence; dense series are aggregated with a detail route.
