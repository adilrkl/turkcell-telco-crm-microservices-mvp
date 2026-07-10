import { describe, it, expect, vi, beforeEach } from "vitest";
import type { ReactNode } from "react";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }));
vi.mock("../lib/axios", () => ({ api: { get: getMock } }));

import { useAuth } from "./useAuth";

function makeWrapper() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
}

describe("useAuth", () => {
  beforeEach(() => getMock.mockReset());

  it("giris yapilmis kullanici + rolleri dondurur; hasRole dogru degerlendirir", async () => {
    getMock.mockResolvedValue({
      data: { username: "csruser", email: null, fullName: null, roles: ["CSR"] },
    });

    const { result } = renderHook(() => useAuth(), { wrapper: makeWrapper() });
    await waitFor(() => expect(result.current.user).not.toBeNull());

    expect(result.current.user?.username).toBe("csruser");
    expect(result.current.hasRole("CSR")).toBe(true);
    expect(result.current.hasRole("ADMIN")).toBe(false);
    // birden fazla rolden herhangi biri yeterli
    expect(result.current.hasRole("ADMIN", "CSR")).toBe(true);
  });

  it("veri yokken (giris yapilmamis / 401) user null ve hasRole daima false", async () => {
    // Oturum yok: /api/me anlamli veri dondurmez, useAuth `data ?? null` ile
    // guvenli null doner. (401'in redirect akisi axios interceptor'unda; axios.test.ts.)
    // DIKKAT: burada asla settle olmayan Promise KULLANMA — sorgu sonsuza kadar
    // "fetching" kalir, vitest dosya teardown'i asilir (CI'da suite kilitlenmisti).
    getMock.mockResolvedValue({ data: null });

    const { result } = renderHook(() => useAuth(), { wrapper: makeWrapper() });

    expect(result.current.user).toBeNull();
    expect(result.current.hasRole("CSR")).toBe(false);
    expect(result.current.hasRole("ADMIN", "CSR", "CUSTOMER")).toBe(false);

    // Sorgu settle olsun ki dosya temiz kapansin (retry: false -> tek deneme).
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.user).toBeNull();
  });
});
