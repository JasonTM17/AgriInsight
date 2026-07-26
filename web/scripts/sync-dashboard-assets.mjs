import { copyFile, mkdir, readFile, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";
import { dirname, isAbsolute, join, relative, resolve } from "node:path";
import process from "node:process";

const repoRoot = resolve(fileURLToPath(new URL("../..", import.meta.url)));
const catalogPath = resolve(repoRoot, "dashboard/assets/generated/catalog.json");
const sourceRoot = resolve(repoRoot, "dashboard/assets/generated");
const outputRoot = resolve(repoRoot, "web/public/visuals");
const maxBytes = 350 * 1024;

const catalog = JSON.parse(await readFile(catalogPath, "utf8"));
if (catalog.version !== 1 || !Array.isArray(catalog.entries) || catalog.entries.length !== 8) {
  throw new Error("Visual catalog must contain exactly eight entries");
}

const areas = new Set();
const filenames = new Set();
let demoCount = 0;
for (const entry of catalog.entries) {
  if (areas.has(entry.area) || filenames.has(entry.filename)) {
    throw new Error(`Duplicate visual catalog key: ${entry.area}/${entry.filename}`);
  }
  areas.add(entry.area);
  filenames.add(entry.filename);
  if (!entry.filename.endsWith(".webp") || !/^[a-f0-9]{64}$/.test(entry.sha256)) {
    throw new Error(`Invalid visual catalog identity: ${entry.filename}`);
  }
  if (!Number.isInteger(entry.width) || !Number.isInteger(entry.height) || entry.width < 1 || entry.height < 1) {
    throw new Error(`Invalid visual dimensions: ${entry.filename}`);
  }
  if (!Number.isInteger(entry.bytes) || entry.bytes < 1 || entry.bytes > maxBytes) {
    throw new Error(`Visual exceeds byte budget: ${entry.filename}`);
  }
  if (!entry.alt || !entry.runtimeUse || !entry.provenance?.tool || !entry.provenance.generatedOn || !entry.provenance.source || !entry.provenance.promptSummary) {
    throw new Error(`Missing visual provenance: ${entry.filename}`);
  }
  if (entry.demoEvidence) demoCount += 1;
}
if (demoCount !== 1 || !catalog.entries.find((entry) => entry.area === "crop-health" && entry.demoEvidence && entry.evidenceBoundary)) {
  throw new Error("Only crop health may be marked as demo evidence");
}

const manifestEntries = [];
for (const entry of catalog.entries) {
  const sourcePath = resolve(sourceRoot, entry.filename);
  const outputPath = resolve(outputRoot, entry.filename);
  const sourceRelative = relative(sourceRoot, sourcePath);
  const outputRelative = relative(outputRoot, outputPath);
  if (isAbsolute(sourceRelative) || sourceRelative.startsWith("..") || isAbsolute(outputRelative) || outputRelative.startsWith("..")) {
    throw new Error(`Visual path escapes the approved roots: ${entry.filename}`);
  }
  const bytes = await readFile(sourcePath);
  if (bytes.length !== entry.bytes || bytes.subarray(0, 4).toString("ascii") !== "RIFF" || bytes.subarray(8, 12).toString("ascii") !== "WEBP") {
    throw new Error(`Visual signature, size, or catalog mismatch: ${entry.filename}`);
  }
  const sha256 = createHash("sha256").update(bytes).digest("hex");
  if (sha256 !== entry.sha256) throw new Error(`Visual hash mismatch: ${entry.filename}`);
  manifestEntries.push({ ...entry });
  if (process.argv.includes("--check")) continue;
  await mkdir(dirname(outputPath), { recursive: true });
  await copyFile(sourcePath, outputPath);
}

if (!process.argv.includes("--check")) {
  await mkdir(outputRoot, { recursive: true });
  await writeFile(
    join(outputRoot, "provenance-manifest.json"),
    JSON.stringify({ version: catalog.version, syncedFrom: "dashboard/assets/generated/catalog.json", entries: manifestEntries }, null, 2) + "\n",
    "utf8"
  );
}

console.log(`${process.argv.includes("--check") ? "Validated" : "Synced"} ${manifestEntries.length} reviewed visuals`);
