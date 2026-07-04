import { describe, it, expect, vi, beforeEach } from "vitest";
import type { ReactNode } from "react";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }));
vi.mock("../lib/axios", () => ({ api: { get: getMock } }));

import { useAuth } from "./useAuth";

function makeWrapper() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
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

  it("veri yokken (giris yapilmamis / 401) user null ve hasRole daima false", () => {
    // /api/me henuz cevap vermemis: useAuth `data ?? null` ile guvenli null doner.
    // (401 pratikte data'yi undefined birakir; sonuc ayni null-guvenlik kontrolu.)
    getMock.mockReturnValue(new Promise<never>(() => {}));

    const { result } = renderHook(() => useAuth(), { wrapper: makeWrapper() });

    expect(result.current.user).toBeNull();
    expect(result.current.hasRole("CSR")).toBe(false);
    expect(result.current.hasRole("ADMIN", "CSR", "CUSTOMER")).toBe(false);
  });
});
