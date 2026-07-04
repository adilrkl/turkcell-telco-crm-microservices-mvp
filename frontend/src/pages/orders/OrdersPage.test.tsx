import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "../../test/utils";
import type { Role } from "../../api/types";

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }));
vi.mock("../../lib/axios", () => ({ api: { get: getMock, post: vi.fn() } }));

const { useAuthMock } = vi.hoisted(() => ({ useAuthMock: vi.fn() }));
vi.mock("../../auth/useAuth", () => ({ useAuth: useAuthMock }));

import { OrdersPage } from "./OrdersPage";

const ORDER = {
  orderId: "aaaaaaaa-1111-2222-3333-444444444444",
  customerId: "bbbbbbbb-1111-2222-3333-444444444444",
  status: "FULFILLED",
  totalAmount: 249.9,
  currency: "TRY",
  tariffCode: "TARIFE_M",
};

function page(content: unknown[]) {
  return { data: { data: { content, number: 0, size: 10, totalElements: content.length } } };
}

function setRole(roles: Role[]) {
  useAuthMock.mockReturnValue({
    user: { username: "u", email: null, fullName: null, roles },
    isLoading: false,
    hasRole: (...r: Role[]) => r.some((x) => roles.includes(x)),
  });
}

describe("OrdersPage rol-bazli aksiyonlar", () => {
  beforeEach(() => {
    getMock.mockReset();
    useAuthMock.mockReset();
    getMock.mockImplementation((url: string) => {
      if (url === "/api/orders") return Promise.resolve(page([ORDER]));
      if (url === "/api/customers") return Promise.resolve(page([]));
      if (url === "/api/catalog/tariffs") return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
  });

  it("siparis listesini render eder (her CSR/ADMIN)", async () => {
    setRole(["CSR"]);
    renderWithProviders(<OrdersPage />);
    expect(await screen.findByText("TARIFE_M")).toBeInTheDocument();
    expect(screen.getByText("249.9 TRY")).toBeInTheDocument();
  });

  it("CSR 'Yeni siparis' ve satir 'Izle' aksiyonunu gorur", async () => {
    setRole(["CSR"]);
    renderWithProviders(<OrdersPage />);
    await screen.findByText("TARIFE_M");
    expect(screen.getByRole("button", { name: /Yeni siparis/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Izle/i })).toBeInTheDocument();
  });

  it("CSR olmayan (yalniz ADMIN) 'Yeni siparis'/'Izle' gormez — GET /orders/{id} CUSTOMER/CSR sinirli", async () => {
    setRole(["ADMIN"]);
    renderWithProviders(<OrdersPage />);
    await screen.findByText("TARIFE_M");
    expect(screen.queryByRole("button", { name: /Yeni siparis/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Izle/i })).not.toBeInTheDocument();
  });

  it("durum kolonu order.status'u Tag olarak gosterir", async () => {
    setRole(["CSR"]);
    renderWithProviders(<OrdersPage />);
    expect(await screen.findByText("FULFILLED")).toBeInTheDocument();
  });
});
