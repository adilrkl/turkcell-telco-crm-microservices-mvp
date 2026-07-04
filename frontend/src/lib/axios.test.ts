import { describe, it, expect, vi, beforeEach } from "vitest";
import { AxiosError } from "axios";

// login modulunu mock'la: onResponseError, loginRedirect'i cagirmali (gercek yonlendirme yok).
// Ayrica axios.ts <-> login.ts dairesel importunu da kirar.
vi.mock("../auth/login", () => ({ loginRedirect: vi.fn() }));

import { onResponseError } from "./axios";
import { loginRedirect } from "../auth/login";

function axiosErrorWith(status: number | undefined, url: string): AxiosError {
  const err = new AxiosError("failed");
  err.config = { url } as never;
  if (status !== undefined) {
    err.response = {
      data: null,
      status,
      statusText: "",
      headers: {},
      config: {} as never,
    };
  }
  return err;
}

describe("onResponseError (401 interceptor)", () => {
  beforeEach(() => {
    vi.mocked(loginRedirect).mockClear();
  });

  it("401'de (normal endpoint) login'e yonlendirir ve hatayi reject eder", async () => {
    const err = axiosErrorWith(401, "/api/orders");
    await expect(onResponseError(err)).rejects.toBe(err);
    expect(loginRedirect).toHaveBeenCalledTimes(1);
  });

  it("/api/me 401'inde YONLENDIRMEZ (giris yapilmamis durumu useAuth yorumlar)", async () => {
    const err = axiosErrorWith(401, "/api/me");
    await expect(onResponseError(err)).rejects.toBe(err);
    expect(loginRedirect).not.toHaveBeenCalled();
  });

  it("401 disi statuslerde yonlendirmez (403/404/500)", async () => {
    for (const status of [403, 404, 500]) {
      const err = axiosErrorWith(status, "/api/customers");
      await expect(onResponseError(err)).rejects.toBe(err);
    }
    expect(loginRedirect).not.toHaveBeenCalled();
  });

  it("response'suz (network) hatada yonlendirmez", async () => {
    const err = axiosErrorWith(undefined, "/api/orders");
    await expect(onResponseError(err)).rejects.toBe(err);
    expect(loginRedirect).not.toHaveBeenCalled();
  });
});
