import { test, expect } from "@playwright/test";

/** Giris yapmis CSR olarak temel gezinme + rol-bazli menu + sayfa yuklenmesi. */
test.describe("Giris sonrasi gezinme (CSR)", () => {
  test("header kullaniciyi ve rol-bazli CSR menulerini gosterir", async ({ page }) => {
    await page.goto("/");
    // exact: fullName "CSR Agent (csruser@...)" span'ini degil, header'daki <strong>csruser</strong>'i hedefle.
    await expect(page.getByText("csruser", { exact: true })).toBeVisible();
    await expect(page.getByRole("menuitem", { name: /Musteriler/i })).toBeVisible();
    await expect(page.getByRole("menuitem", { name: /Siparisler/i })).toBeVisible();
    await expect(page.getByRole("menuitem", { name: /Destek Talepleri/i })).toBeVisible();
    await expect(page.getByRole("menuitem", { name: /Tarifeler/i })).toBeVisible();
  });

  test("Musteriler sayfasi tablo + arama + 'Yeni musteri' ile yuklenir", async ({ page }) => {
    await page.goto("/customers");
    await expect(page.getByRole("button", { name: /Yeni musteri/i })).toBeVisible();
    // Tablo basliklari (exact: 'Ad' aksi halde 'Soyad'i da yakalar)
    await expect(page.getByRole("columnheader", { name: "Ad", exact: true })).toBeVisible();
    await expect(page.getByRole("columnheader", { name: "Soyad", exact: true })).toBeVisible();
    await expect(page.getByRole("columnheader", { name: "Durum", exact: true })).toBeVisible();
  });

  test("Tarifeler sayfasi tarife listesini (TARIFE_*) gosterir", async ({ page }) => {
    await page.goto("/tariffs");
    await expect(page.getByText(/TARIFE_/).first()).toBeVisible();
  });

  test("Siparisler sayfasi CSR icin 'Yeni siparis' aksiyonuyla yuklenir", async ({ page }) => {
    await page.goto("/orders");
    await expect(page.getByRole("button", { name: /Yeni siparis/i })).toBeVisible();
    await expect(page.getByRole("columnheader", { name: "Tarife" })).toBeVisible();
  });

  test("Destek Talepleri sayfasi tablo basliklariyla yuklenir", async ({ page }) => {
    await page.goto("/tickets");
    await expect(page.getByRole("columnheader", { name: "Kategori" })).toBeVisible();
    await expect(page.getByRole("columnheader", { name: "Oncelik" })).toBeVisible();
  });
});
