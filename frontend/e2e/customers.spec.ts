import { test, expect } from "@playwright/test";

/**
 * Canli backend'e karsi G9 TCKN/VKN dogrulamasi (FR-01) — ayni zamanda FE bug-fix'in
 * (backend 422 mesajinin yuzeye cikmasi) uctan uca kaniti.
 */
test.describe("Musteri olusturma - G9 kimlik dogrulama (canli stack)", () => {
  test("gecersiz TCKN: backend 422 'Gecersiz TCKN' mesaji kullaniciya gorunur", async ({ page }) => {
    await page.goto("/customers");
    await page.getByRole("button", { name: /Yeni musteri/i }).click();

    await page.getByLabel("Ad", { exact: true }).fill("Gecersiz");
    await page.getByLabel("Soyad", { exact: true }).fill("Kimlik");
    await page.getByLabel("TCKN", { exact: true }).fill("12345678901"); // checksum tutmaz
    await page.getByRole("button", { name: /Tamam|^OK$/ }).click();

    // apiErrorMessage sayesinde generic "Kaydetme basarisiz" degil, backend mesaji cikar.
    await expect(page.getByText("Gecersiz TCKN")).toBeVisible();
  });

  test("gecerli TCKN: musteri PENDING olusur ve listede gorunur", async ({ page }) => {
    const soyad = "PWT" + Date.now();
    await page.goto("/customers");
    await page.getByRole("button", { name: /Yeni musteri/i }).click();

    await page.getByLabel("Ad", { exact: true }).fill("Playwright");
    await page.getByLabel("Soyad", { exact: true }).fill(soyad);
    await page.getByLabel("TCKN", { exact: true }).fill("10000000146"); // gecerli TCKN
    await page.getByRole("button", { name: /Tamam|^OK$/ }).click();

    // Basari mesaji (backend "Musteri olusturuldu" / fallback "Kaydedildi")
    await expect(page.getByText(/Musteri olusturuldu|Kaydedildi/)).toBeVisible();

    // Olusan kayit yeni musteri PENDING dogar (G3): aramayla dogrula.
    await page.getByPlaceholder(/ad \/ soyad \/ TCKN ara/i).fill(soyad);
    await page.getByPlaceholder(/ad \/ soyad \/ TCKN ara/i).press("Enter");
    await expect(page.getByRole("cell", { name: soyad })).toBeVisible();
    await expect(page.getByText("PENDING").first()).toBeVisible();
  });

  test("Kurumsal secilince kimlik etiketi VKN olur (G9 dinamik etiket)", async ({ page }) => {
    await page.goto("/customers");
    await page.getByRole("button", { name: /Yeni musteri/i }).click();

    await expect(page.getByText("TCKN")).toBeVisible();
    // AntD Select: inner search input overlay'e takildigi icin selector container'ina tikla.
    await page.getByRole("dialog").locator(".ant-select-selector").first().click();
    await page.getByText("Kurumsal", { exact: true }).click();
    await expect(page.getByText("VKN")).toBeVisible();
  });
});
