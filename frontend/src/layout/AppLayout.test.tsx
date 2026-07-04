import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "../test/utils";
import type { Role } from "../api/types";

const { useAuthMock } = vi.hoisted(() => ({ useAuthMock: vi.fn() }));
vi.mock("../auth/useAuth", () => ({ useAuth: useAuthMock }));
// logout tam sayfa yonlendirme yapmasin diye login modulunu no-op mock'la.
vi.mock("../auth/login", () => ({ logout: vi.fn(), loginRedirect: vi.fn() }));

import { AppLayout } from "./AppLayout";

function setRoles(roles: Role[] | null) {
  useAuthMock.mockReturnValue({
    user: roles ? { username: "u", email: null, fullName: null, roles } : null,
    isLoading: false,
    hasRole: (...r: Role[]) => roles != null && r.some((x) => roles.includes(x)),
  });
}

describe("AppLayout rol-bazli menu", () => {
  beforeEach(() => useAuthMock.mockReset());

  it("rolsuz (yalniz oturum) sadece herkese acik menuleri gosterir", () => {
    setRoles([]);
    renderWithProviders(<AppLayout />);
    // roles:[] olan menuler herkese acik
    expect(screen.getByRole("menuitem", { name: /Ana Sayfa/i })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /Tarifeler/i })).toBeInTheDocument();
    // CSR/ADMIN gerektirenler gorunmez
    expect(screen.queryByRole("menuitem", { name: /Musteriler/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: /Siparisler/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: /Faturalar/i })).not.toBeInTheDocument();
  });

  it("CSR tum CSR menulerini gorur (Musteriler/Siparisler/Talepler/Faturalar)", () => {
    setRoles(["CSR"]);
    renderWithProviders(<AppLayout />);
    expect(screen.getByRole("menuitem", { name: /Musteriler/i })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /Destek Talepleri/i })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /Siparisler/i })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /Faturalar/i })).toBeInTheDocument();
  });

  it("BILLING_ADMIN Faturalar'i gorur ama Musteriler'i gormez", () => {
    setRoles(["BILLING_ADMIN"]);
    renderWithProviders(<AppLayout />);
    expect(screen.getByRole("menuitem", { name: /Faturalar/i })).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: /Musteriler/i })).not.toBeInTheDocument();
  });

  it("kullanici adi ve rol etiketi header'da gorunur", () => {
    setRoles(["CSR"]);
    renderWithProviders(<AppLayout />);
    expect(screen.getByText("u")).toBeInTheDocument();
    expect(screen.getByText("CSR")).toBeInTheDocument();
  });
});
