const metadata = {
  freshness: {
    artifactAgeHours: 2,
    dataStatus: "current",
    maxAgeHours: 24
  },
  lineage: {
    asOf: "2026-07-18",
    contractVersion: "1.0.0",
    generatedAt: "2026-07-18T01:00:00Z",
    manifestFingerprint: "a".repeat(64),
    runId: "synthetic-2026-07-18"
  },
  scope: {
    appliedFilter: null,
    farmCodes: ["FARM-001"],
    tenantId: "20000000-0000-4000-8000-000000000001",
    tenantWide: true,
    warehouseCodes: []
  }
} as const;

export const cropHealthEnvelopeFixture = {
  ...metadata,
  payload: {
    alerts: [],
    assessmentMethod: "rule-based-heuristic",
    evidenceSignals: [
      { name: "monitoredFields", unit: null, value: 1 }
    ],
    fields: [
      {
        airHumidityPct: 79,
        areaHa: 12.5,
        batteryPct: 88,
        cropCode: "RICE",
        cropName: "Lúa",
        farmCode: "FARM-001",
        farmName: "Nông trại An Phú",
        fieldCode: "FIELD-001",
        fieldName: "Khu Bắc",
        lastReadingAt: "2026-07-18T00:00:00Z",
        latitude: 10.7,
        longitude: 106.6,
        maxAffectedAreaPct: 0,
        maxMortalityPct: 0,
        pestCases90d: 0,
        rainfall7dMm: 41,
        readingCount7d: 168,
        recommendedAction: "Tiếp tục đối chiếu theo lịch vận hành.",
        riskScore: 12,
        riskStatus: "normal",
        sensorAgeDays: 0.2,
        soilMoisturePct: 61,
        soilPh: 6.4,
        temperatureC: 29
      }
    ],
    page: { hasMore: false, limit: 50, offset: 0, total: 1 },
    pestIncidentsWeekly: [],
    severity: "low",
    summary: {
      averageSoilMoisturePct: 61,
      averageSoilPh: 6.4,
      averageTemperatureC: 29,
      highRiskFields: 0,
      monitoredFields: 1,
      offlineSensors: 0,
      pestCases90d: 0,
      readings7d: 168,
      watchFields: 0
    }
  }
} as const;

const qualityScore = {
  completenessPct: 99.5,
  freshnessAgeDays: 0,
  freshnessPct: 100,
  uniquenessPct: 100,
  validityPct: 99.8
};

export const dataQualityEnvelopeFixture = {
  ...metadata,
  payload: {
    assessmentMethod: "rule-based-heuristic",
    checks: {
      after: [
        {
          check: "required_values",
          failedRows: 0,
          severity: "error",
          table: "sensor_readings",
          totalRows: 1000
        }
      ],
      before: []
    },
    evidenceSignals: [
      { name: "completeness_pct", unit: "pct", value: 99.5 }
    ],
    remediationActions: {
      codesCanonicalized: 2,
      duplicatesRemoved: 1,
      rowsQuarantined: 3,
      unitsConvertedToBase: 4,
      unitsConvertedToKg: 4
    },
    scores: { after: qualityScore, before: qualityScore },
    severity: "none",
    status: "passed"
  }
} as const;
