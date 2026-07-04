/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Dev'de SPA :5173, BFF :9000 — cookie/CSRF'in same-origin akmasi icin
// /api, /oauth2, /login, /logout BFF'e proxy'lenir (FRONTEND.md §11).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": { target: "http://localhost:9000", changeOrigin: true },
      "/oauth2": { target: "http://localhost:9000", changeOrigin: true },
      "/login": { target: "http://localhost:9000", changeOrigin: true },
      "/logout": { target: "http://localhost:9000", changeOrigin: true },
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
  },
});
