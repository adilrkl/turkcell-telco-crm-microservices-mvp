import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "../test/utils";
import type { Role } from "../api/types";

const { useAuthMock } = vi.hoisted(() => ({ useAuthMock: vi.fn() }));
vi.mock("./useAuth", () => ({ useAuth: useAuthMock }));

import { RequireRole } from "./RequireRole";

function setAuth(opts: { user: { roles: Role[] } | null; isLoading?: boolean }) {
  useAuthMock.mockReturnValue({
    user: opts.user,
    isLoading: opts.isLoading ?? false,
    hasRole: (...roles: Role[]) =>
      opts.user != null && roles.some((r) => opts.user!.roles.includes(r)),
  });
}

describe("RequireRole (rol guard)", () => {
  beforeEach(() => useAuthMock.mockReset());

  it("yukleniyorken cocuk render edilmez (spinner)", () => {
    setAuth({ user: null, isLoading: true });
    renderWithProviders(
      <RequireRole roles={["CSR"]}>
        <div>gizli icerik</div>
      </RequireRole>,
    );
    expect(screen.queryByText("gizli icerik")).not.toBeInTheDocument();
  });

  it("oturum yoksa 'Oturum acilmamis' + Giris yap gosterir", () => {
    setAuth({ user: null });
    renderWithProviders(
      <RequireRole roles={["CSR"]}>
        <div>gizli icerik</div>
      </RequireRole>,
    );
    expect(screen.getByText("Oturum acilmamis")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Giris yap/i })).toBeInTheDocument();
    expect(screen.queryByText("gizli icerik")).not.toBeInTheDocument();
  });

  it("rol yetersizse 403 gosterir, icerik gizlenir", () => {
    setAuth({ user: { roles: ["CUSTOMER"] } });
    renderWithProviders(
      <RequireRole roles={["CSR", "ADMIN"]}>
        <div>gizli icerik</div>
      </RequireRole>,
    );
    expect(screen.getByText("Bu sayfa icin yetkiniz yok.")).toBeInTheDocument();
    expect(screen.queryByText("gizli icerik")).not.toBeInTheDocument();
  });

  it("yetkili rolde cocuk icerigi render edilir", () => {
    setAuth({ user: { roles: ["CSR"] } });
    renderWithProviders(
      <RequireRole roles={["CSR", "ADMIN"]}>
        <div>gizli icerik</div>
      </RequireRole>,
    );
    expect(screen.getByText("gizli icerik")).toBeInTheDocument();
  });
});
