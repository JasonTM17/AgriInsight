import catalogJson from "../../../dashboard/assets/generated/catalog.json";

export type VisualProvenance = Readonly<{
  tool: string;
  generatedOn: string;
  source: string;
  promptSummary: string;
}>;

export type VisualCatalogEntry = Readonly<{
  area: string;
  filename: string;
  title: string;
  description: string;
  alt: string;
  runtimeUse: string;
  width: number;
  height: number;
  bytes: number;
  sha256: string;
  provenance: VisualProvenance;
  demoEvidence: boolean;
  evidenceBoundary?: string;
}>;

export type VisualCatalog = Readonly<{
  version: number;
  generatedOn: string;
  source: string;
  entries: readonly VisualCatalogEntry[];
}>;

const HASH_PATTERN = /^[a-f0-9]{64}$/;
const MAX_BYTES = 350 * 1024;

export function assertVisualCatalog(value: unknown): asserts value is VisualCatalog {
  if (!value || typeof value !== "object") throw new Error("Visual catalog must be an object");
  const catalog = value as Partial<VisualCatalog>;
  if (catalog.version !== 1 || !Array.isArray(catalog.entries) || catalog.entries.length !== 8) {
    throw new Error("Visual catalog must contain exactly eight version-one entries");
  }
  if (typeof catalog.generatedOn !== "string" || typeof catalog.source !== "string") {
    throw new Error("Visual catalog metadata is incomplete");
  }
  const areas = new Set<string>();
  const filenames = new Set<string>();
  let demoCount = 0;
  for (const entry of catalog.entries) {
    if (!entry || typeof entry !== "object") throw new Error("Visual catalog entry is invalid");
    if (areas.has(entry.area) || filenames.has(entry.filename)) throw new Error("Visual catalog keys must be unique");
    areas.add(entry.area);
    filenames.add(entry.filename);
    if (!entry.filename.endsWith(".webp") || !HASH_PATTERN.test(entry.sha256)) {
      throw new Error(`Invalid visual identity for ${entry.filename}`);
    }
    if (!Number.isInteger(entry.width) || !Number.isInteger(entry.height) || entry.width < 1 || entry.height < 1) {
      throw new Error(`Invalid dimensions for ${entry.filename}`);
    }
    if (!Number.isInteger(entry.bytes) || entry.bytes < 1 || entry.bytes > MAX_BYTES) {
      throw new Error(`Invalid byte budget for ${entry.filename}`);
    }
    if (!entry.alt || !entry.runtimeUse || !entry.provenance?.tool || !entry.provenance.generatedOn || !entry.provenance.source || !entry.provenance.promptSummary) {
      throw new Error(`Missing provenance or accessible description for ${entry.filename}`);
    }
    if (entry.demoEvidence) demoCount += 1;
  }
  const cropHealth = catalog.entries.find((entry) => entry.area === "crop-health");
  if (demoCount !== 1 || !cropHealth?.demoEvidence || !cropHealth.evidenceBoundary) {
    throw new Error("Crop health must be the only explicitly marked demo evidence");
  }
}

assertVisualCatalog(catalogJson);

export const VISUAL_CATALOG = catalogJson;
export const VISUAL_CATALOG_BY_AREA = new Map(
  VISUAL_CATALOG.entries.map((entry) => [entry.area, entry])
);
