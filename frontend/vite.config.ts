/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Dev'de SPA :5173, BFF :9000 — cookie/CSRF'in same-origin akmasi icin
// /api, /oauth2, /login, /logout BFF'e proxy'lenir (FRONTEND.md §11).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // KRITIK (oauth2/login/logout icin changeOrigin=false): BFF, oauth2Login redirect_uri'sini
    // ve logout post_logout_redirect_uri'sini gelen HOST header'indan uretir. changeOrigin:true
    // Host'u :9000 yapinca Keycloak login sonrasi tarayiciyi :9000'e dondurur -> orada SPA yok,
    // Whitelabel 404 gorunur. Host'u :5173 birakinca redirect_uri :5173 olur (Keycloak client'inda
    // :5173 zaten kayitli, FRONTEND.md §6) ve akis SPA'ya geri doner. /api redirect uretmez (401).
    proxy: {
      "/api": { target: "http://localhost:9000", changeOrigin: true },
      "/oauth2": { target: "http://localhost:9000", changeOrigin: false },
      "/login": { target: "http://localhost:9000", changeOrigin: false },
      "/logout": { target: "http://localhost:9000", changeOrigin: false },
    },
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    restoreMocks: true,
    css: false,
    // Yalniz birim/bilesen testleri (src). Playwright E2E'leri (e2e/*.spec.ts) haric.
    include: ["src/**/*.test.{ts,tsx}"],
    // Windows'ta varsayilan 'forks' pool'u child-process IPC'de takilabiliyor
    // (worker'lar idle kalir, kosu asilir). 'threads' (worker_threads) stabil calisir.
    pool: "threads",
  },
});
