#!/usr/bin/env node
/**
 * OpenAPI spec'lerinden tam tipli axios client'lari uretir (FRONTEND.md §5).
 * Servisler AYAKTA olmali (spec'ler dogrudan servis portundan cekilir; gateway'den degil).
 * Cikti: src/api/generated/<servis>/ (.gitignore'ludur — istenildiginde yeniden uretilir).
 */
import { execSync } from "node:child_process";

const SERVICES = [
  ["identity", "http://localhost:8081/v3/api-docs"],
  ["customer", "http://localhost:8082/v3/api-docs"],
  ["catalog", "http://localhost:8083/v3/api-docs"],
  ["order", "http://localhost:8084/v3/api-docs"],
  ["subscription", "http://localhost:8085/v3/api-docs"],
  ["usage", "http://localhost:8086/v3/api-docs"],
  ["billing", "http://localhost:8087/v3/api-docs"],
  ["ticket", "http://localhost:8090/v3/api-docs"],
];

for (const [name, url] of SERVICES) {
  console.log(`\n=== ${name} <- ${url}`);
  try {
    execSync(
      `npx openapi-generator-cli generate -g typescript-axios -i ${url} ` +
        `-o src/api/generated/${name} --additional-properties=supportsES6=true,withSeparateModelsAndApi=false`,
      { stdio: "inherit" },
    );
  } catch {
    console.error(`!! ${name} uretilemedi (servis ayakta mi?) — atlaniyor`);
  }
}
