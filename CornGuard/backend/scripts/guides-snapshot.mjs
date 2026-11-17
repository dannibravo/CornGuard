// Moves treatment guides between the backend and the app's bundled snapshot
// (android/app/src/main/assets/treatment/guides-snapshot.json). The app shows that snapshot only
// until its first successful sync; after that, what admins publish in the admin website wins.
//
//   node scripts/guides-snapshot.mjs export              server → snapshot (do this before each release)
//   node scripts/guides-snapshot.mjs import              snapshot → server, only diseases without a guide
//   node scripts/guides-snapshot.mjs import --overwrite  snapshot → server, replacing existing guides
//
// Uses the deployment in .env.local (CONVEX_URL / CONVEX_DEPLOYMENT).

import { ConvexHttpClient } from "convex/browser";
import { anyApi } from "convex/server";
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const SNAPSHOT = new URL("../../android/app/src/main/assets/treatment/guides-snapshot.json", import.meta.url);
const url = readFileSync(new URL("../.env.local", import.meta.url), "utf8").match(/^CONVEX_URL=(.+)$/m)?.[1]?.trim();
if (!url) throw new Error("Set CONVEX_URL in .env.local.");

const [command] = process.argv.slice(2);

if (command === "export") {
  const guides = await new ConvexHttpClient(url).query(anyApi.diseaseReferences.list, {});
  if (guides.length === 0) throw new Error("The server has no guides; refusing to write an empty snapshot.");
  guides.sort((a, b) => a.diseaseCode.localeCompare(b.diseaseCode));
  writeFileSync(SNAPSHOT, JSON.stringify(guides, null, 4) + "\n");
  console.log(`Wrote ${guides.length} guide(s) from ${url} to the app snapshot.`);
} else if (command === "import") {
  const guides = JSON.parse(readFileSync(SNAPSHOT, "utf8"));
  const args = JSON.stringify({ overwrite: process.argv.includes("--overwrite"), guides });
  // Internal mutation: only the Convex CLI (deploy credentials) can call it. Run the CLI with node
  // directly, not via npx/cmd: cmd caps a command line at ~8 KB and mangles the JSON's quotes.
  const cli = fileURLToPath(new URL("../node_modules/convex/bin/main.js", import.meta.url));
  const out = execFileSync(process.execPath, [cli, "run", "diseaseReferences:importSnapshot", args], {
    encoding: "utf8", cwd: fileURLToPath(new URL("..", import.meta.url)),
  });
  console.log(out.trim());
} else {
  console.log("usage: node scripts/guides-snapshot.mjs export | import [--overwrite]");
  process.exit(1);
}
