import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

import openapiTS, { astToString, COMMENT_HEADER } from "openapi-typescript";

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const repositoryRoot = resolve(webRoot, "..");
const checkOnly = process.argv.includes("--check");
const contracts = [
  {
    input: resolve(
      repositoryRoot,
      "backend/src/main/resources/contracts/agriinsight-api-v1.openapi.json"
    ),
    output: resolve(webRoot, "src/server/generated/backend/schema.d.ts")
  },
  {
    input: resolve(
      repositoryRoot,
      "docs/contracts/agriinsight-analytics-v1.openapi.json"
    ),
    output: resolve(webRoot, "src/server/generated/analytics/schema.d.ts")
  }
];

let drifted = false;
for (const contract of contracts) {
  const ast = await openapiTS(pathToFileURL(contract.input), {
    alphabetize: true,
    immutable: true,
    rootTypes: true
  });
  const generated = `${COMMENT_HEADER}${astToString(ast)}`;
  let current = "";
  try {
    current = await readFile(contract.output, "utf8");
  } catch (error) {
    if (error?.code !== "ENOENT") throw error;
  }

  if (current === generated) continue;
  if (checkOnly) {
    drifted = true;
    process.stderr.write(`Generated OpenAPI types drifted: ${contract.output}\n`);
    continue;
  }
  await mkdir(dirname(contract.output), { recursive: true });
  await writeFile(contract.output, generated, "utf8");
  process.stdout.write(`Generated ${contract.output}\n`);
}

if (drifted) process.exitCode = 1;
