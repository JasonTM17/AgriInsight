PRAGMA foreign_keys = ON;

CREATE TABLE dim_date (
    date_key INTEGER PRIMARY KEY,
    full_date TEXT NOT NULL UNIQUE,
    year INTEGER NOT NULL,
    quarter INTEGER NOT NULL,
    month INTEGER NOT NULL,
    month_name TEXT NOT NULL,
    week INTEGER NOT NULL,
    day INTEGER NOT NULL
);

CREATE TABLE dim_farm (
    farm_key INTEGER PRIMARY KEY,
    farm_code TEXT NOT NULL UNIQUE,
    farm_name TEXT NOT NULL,
    province TEXT NOT NULL,
    registered_area_ha REAL NOT NULL CHECK (registered_area_ha > 0),
    latitude REAL NOT NULL,
    longitude REAL NOT NULL
);

CREATE TABLE dim_field (
    field_key INTEGER PRIMARY KEY,
    field_code TEXT NOT NULL UNIQUE,
    farm_key INTEGER NOT NULL REFERENCES dim_farm(farm_key),
    field_name TEXT NOT NULL,
    area_ha REAL NOT NULL CHECK (area_ha > 0),
    soil_type TEXT NOT NULL,
    irrigation_type TEXT NOT NULL,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL
);

CREATE TABLE dim_crop (
    crop_key INTEGER PRIMARY KEY,
    crop_code TEXT NOT NULL UNIQUE,
    crop_name TEXT NOT NULL,
    category TEXT NOT NULL
);

CREATE TABLE dim_season (
    season_key INTEGER PRIMARY KEY,
    season_code TEXT NOT NULL UNIQUE,
    field_key INTEGER NOT NULL REFERENCES dim_field(field_key),
    farm_key INTEGER NOT NULL REFERENCES dim_farm(farm_key),
    crop_key INTEGER NOT NULL REFERENCES dim_crop(crop_key),
    start_date TEXT NOT NULL,
    expected_harvest_date TEXT NOT NULL,
    target_yield_kg REAL NOT NULL CHECK (target_yield_kg > 0),
    budget_cost_vnd REAL NOT NULL CHECK (budget_cost_vnd > 0),
    status TEXT NOT NULL CHECK (status IN ('active', 'completed'))
);

CREATE TABLE dim_activity_type (
    activity_type_key INTEGER PRIMARY KEY,
    activity_type TEXT NOT NULL UNIQUE
);

CREATE TABLE dim_warehouse (
    warehouse_key INTEGER PRIMARY KEY,
    warehouse_code TEXT NOT NULL UNIQUE,
    farm_key INTEGER NOT NULL REFERENCES dim_farm(farm_key),
    warehouse_name TEXT NOT NULL,
    capacity_value_vnd REAL NOT NULL CHECK (capacity_value_vnd > 0),
    temperature_controlled INTEGER NOT NULL CHECK (temperature_controlled IN (0, 1))
);

CREATE TABLE dim_material (
    material_key INTEGER PRIMARY KEY,
    material_code TEXT NOT NULL UNIQUE,
    material_name TEXT NOT NULL,
    category TEXT NOT NULL,
    base_unit TEXT NOT NULL CHECK (base_unit IN ('kg', 'liter', 'piece')),
    reorder_point REAL NOT NULL CHECK (reorder_point > 0),
    target_stock_level REAL NOT NULL CHECK (target_stock_level > reorder_point),
    shelf_life_days INTEGER NOT NULL CHECK (shelf_life_days > 0),
    reference_unit_cost_vnd REAL NOT NULL CHECK (reference_unit_cost_vnd > 0)
);

CREATE TABLE dim_supplier (
    supplier_key INTEGER PRIMARY KEY,
    supplier_code TEXT NOT NULL UNIQUE,
    supplier_name TEXT NOT NULL,
    province TEXT NOT NULL,
    quality_rating REAL NOT NULL CHECK (quality_rating BETWEEN 0 AND 5)
);

CREATE TABLE dim_sensor (
    sensor_key INTEGER PRIMARY KEY,
    sensor_code TEXT NOT NULL UNIQUE,
    field_key INTEGER NOT NULL REFERENCES dim_field(field_key),
    farm_key INTEGER NOT NULL REFERENCES dim_farm(farm_key),
    sensor_model TEXT NOT NULL,
    installed_at TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('active', 'maintenance', 'retired'))
);

CREATE TABLE dim_pest_type (
    pest_key INTEGER PRIMARY KEY,
    pest_code TEXT NOT NULL UNIQUE,
    pest_name TEXT NOT NULL,
    pest_category TEXT NOT NULL
);

CREATE TABLE fact_crop_activity (
    activity_id TEXT PRIMARY KEY,
    date_key INTEGER NOT NULL REFERENCES dim_date(date_key),
    farm_key INTEGER NOT NULL REFERENCES dim_farm(farm_key),
    field_key INTEGER NOT NULL REFERENCES dim_field(field_key),
    crop_key INTEGER NOT NULL REFERENCES dim_crop(crop_key),
    season_key INTEGER NOT NULL REFERENCES dim_season(season_key),
    activity_type_key INTEGER NOT NULL REFERENCES dim_activity_type(activity_type_key),
    occurred_at TEXT NOT NULL,
    material_name TEXT NOT NULL,
    quantity_kg REAL NOT NULL CHECK (quantity_kg >= 0),
    labor_hours REAL NOT NULL CHECK (labor_hours >= 0),
    material_cost_vnd REAL NOT NULL CHECK (material_cost_vnd >= 0),
    labor_cost_vnd REAL NOT NULL CHECK (labor_cost_vnd >= 0),
    total_cost_vnd REAL NOT NULL CHECK (total_cost_vnd >= 0),
    notes TEXT
);

CREATE TABLE fact_harvest (
    harvest_id TEXT PRIMARY KEY,
    date_key INTEGER NOT NULL REFERENCES dim_date(date_key),
    farm_key INTEGER NOT NULL REFERENCES dim_farm(farm_key),
    field_key INTEGER NOT NULL REFERENCES dim_field(field_key),
    crop_key INTEGER NOT NULL REFERENCES dim_crop(crop_key),
    season_key INTEGER NOT NULL REFERENCES dim_season(season_key),
    harvested_at TEXT NOT NULL,
    harvest_quantity_kg REAL NOT NULL CHECK (harvest_quantity_kg >= 0),
    waste_quantity_kg REAL NOT NULL CHECK (waste_quantity_kg >= 0),
    quality_grade TEXT NOT NULL,
    revenue_vnd REAL NOT NULL CHECK (revenue_vnd >= 0)
);

CREATE TABLE fact_inventory_transaction (
    transaction_id TEXT PRIMARY KEY,
    date_key INTEGER NOT NULL REFERENCES dim_date(date_key),
    warehouse_key INTEGER NOT NULL REFERENCES dim_warehouse(warehouse_key),
    farm_key INTEGER NOT NULL REFERENCES dim_farm(farm_key),
    material_key INTEGER NOT NULL REFERENCES dim_material(material_key),
    supplier_key INTEGER REFERENCES dim_supplier(supplier_key),
    transaction_date TEXT NOT NULL,
    transaction_type TEXT NOT NULL CHECK (transaction_type IN ('IN', 'OUT')),
    quantity_base_unit REAL NOT NULL CHECK (quantity_base_unit > 0),
    base_unit TEXT NOT NULL CHECK (base_unit IN ('kg', 'liter', 'piece')),
    unit_cost_base_unit_vnd REAL NOT NULL CHECK (unit_cost_base_unit_vnd >= 0),
    total_amount_vnd REAL NOT NULL CHECK (total_amount_vnd >= 0),
    batch_code TEXT,
    expiry_date TEXT
);

CREATE TABLE fact_sensor_reading (
    reading_id TEXT PRIMARY KEY,
    date_key INTEGER NOT NULL REFERENCES dim_date(date_key),
    sensor_key INTEGER NOT NULL REFERENCES dim_sensor(sensor_key),
    farm_key INTEGER NOT NULL REFERENCES dim_farm(farm_key),
    field_key INTEGER NOT NULL REFERENCES dim_field(field_key),
    observed_at TEXT NOT NULL,
    temperature_c REAL NOT NULL CHECK (temperature_c BETWEEN -10 AND 60),
    air_humidity_pct REAL NOT NULL CHECK (air_humidity_pct BETWEEN 0 AND 100),
    soil_moisture_pct REAL NOT NULL CHECK (soil_moisture_pct BETWEEN 0 AND 100),
    soil_ph REAL NOT NULL CHECK (soil_ph BETWEEN 0 AND 14),
    rainfall_mm REAL NOT NULL CHECK (rainfall_mm BETWEEN 0 AND 1000),
    battery_pct REAL NOT NULL CHECK (battery_pct BETWEEN 0 AND 100)
);

CREATE TABLE fact_weather_daily (
    weather_id TEXT PRIMARY KEY,
    date_key INTEGER NOT NULL REFERENCES dim_date(date_key),
    farm_key INTEGER NOT NULL REFERENCES dim_farm(farm_key),
    weather_date TEXT NOT NULL,
    temperature_avg_c REAL NOT NULL CHECK (temperature_avg_c BETWEEN -10 AND 60),
    humidity_avg_pct REAL NOT NULL CHECK (humidity_avg_pct BETWEEN 0 AND 100),
    rainfall_mm REAL NOT NULL CHECK (rainfall_mm BETWEEN 0 AND 1000),
    wind_speed_kph REAL NOT NULL CHECK (wind_speed_kph BETWEEN 0 AND 250),
    source TEXT NOT NULL
);

CREATE TABLE fact_crop_health_observation (
    observation_id TEXT PRIMARY KEY,
    date_key INTEGER NOT NULL REFERENCES dim_date(date_key),
    farm_key INTEGER NOT NULL REFERENCES dim_farm(farm_key),
    field_key INTEGER NOT NULL REFERENCES dim_field(field_key),
    crop_key INTEGER NOT NULL REFERENCES dim_crop(crop_key),
    season_key INTEGER NOT NULL REFERENCES dim_season(season_key),
    pest_key INTEGER NOT NULL REFERENCES dim_pest_type(pest_key),
    observed_at TEXT NOT NULL,
    severity TEXT NOT NULL CHECK (severity IN ('none', 'low', 'medium', 'high')),
    affected_area_pct REAL NOT NULL CHECK (affected_area_pct BETWEEN 0 AND 100),
    plant_mortality_pct REAL NOT NULL CHECK (plant_mortality_pct BETWEEN 0 AND 100),
    notes TEXT
);

CREATE TABLE etl_run (
    run_id TEXT PRIMARY KEY,
    seed INTEGER NOT NULL,
    as_of_date TEXT NOT NULL,
    source_name TEXT NOT NULL
);

CREATE INDEX idx_activity_date ON fact_crop_activity(date_key);
CREATE INDEX idx_activity_farm ON fact_crop_activity(farm_key);
CREATE INDEX idx_activity_season ON fact_crop_activity(season_key);
CREATE INDEX idx_harvest_date ON fact_harvest(date_key);
CREATE INDEX idx_harvest_farm ON fact_harvest(farm_key);
CREATE INDEX idx_harvest_season ON fact_harvest(season_key);
CREATE INDEX idx_inventory_date ON fact_inventory_transaction(date_key);
CREATE INDEX idx_inventory_warehouse_material
    ON fact_inventory_transaction(warehouse_key, material_key);
CREATE INDEX idx_sensor_reading_date ON fact_sensor_reading(date_key);
CREATE INDEX idx_sensor_reading_field ON fact_sensor_reading(field_key);
CREATE INDEX idx_weather_date_farm ON fact_weather_daily(date_key, farm_key);
CREATE INDEX idx_health_date_field ON fact_crop_health_observation(date_key, field_key);
CREATE INDEX idx_health_season ON fact_crop_health_observation(season_key);
