import { test as setup, expect } from "@playwright/test";

const authFile = "e2e/.auth/user.json";

/**
 * Keycloak (telco-crm realm) ile csruser giris yapar ve session cookie'sini kaydeder.
 * Akis: /customers (rol-guard) -> "Giris yap" -> BFF oauth2 -> Keycloak login formu -> geri.
 * Oturum, BFF GET /api/me (200 + CSR rolu) ile dogrulanir; UI beklemesine bagli kalmaz.
 */
setup("keycloak ile giris (csruser)", async ({ page }) => {
  await page.goto("/customers");
  await page.getByRole("button", { name: /Giris yap/i }).click();

  await page.waitForSelector("#username", { timeout: 30_000 });
  await page.fill("#username", "csruser");
  await page.fill("#password", "test12345");
  await page.click("#kc-login");

  // Callback + BFF post-login redirect tamamlanana kadar bekle.
  await page.waitForURL("http://localhost:9000/", { timeout: 30_000 }).catch(() => {});

  // Oturum gecerli mi? Session cookie ile /api/me 200 + CSR rolu donmeli.
  const me = await page.request.get("http://localhost:5173/api/me");
  expect(me.status()).toBe(200);
  const body = await me.json();
  expect(body.username).toBe("csruser");
  expect(body.roles).toContain("CSR");

  await page.context().storageState({ path: authFile });
});
