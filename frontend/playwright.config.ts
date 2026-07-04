import { defineConfig, devices } from "@playwright/test";

/**
 * E2E: gercek stack'e karsi (frontend :5173 -> BFF :9000 -> gateway -> servisler + Keycloak :8095).
 * `setup` projesi Keycloak ile giris yapip session cookie'sini storageState'e kaydeder;
 * `chromium` projesi bu state ile giris yapmis olarak kosar.
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 90_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"]],
  use: {
    baseURL: "http://localhost:5173",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    actionTimeout: 20_000,
    navigationTimeout: 30_000,
  },
  projects: [
    { name: "setup", testMatch: /auth\.setup\.ts/ },
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"], storageState: "e2e/.auth/user.json" },
      dependencies: ["setup"],
      testIgnore: /auth\.setup\.ts/,
    },
  ],
});
