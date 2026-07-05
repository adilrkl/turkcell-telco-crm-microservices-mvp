import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "../../test/utils";
import type { Role } from "../../api/types";

const { getMock, postMock } = vi.hoisted(() => ({ getMock: vi.fn(), postMock: vi.fn() }));
vi.mock("../../lib/axios", () => ({ api: { get: getMock, post: postMock } }));

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

const PENDING_ORDER = {
  ...ORDER,
  orderId: "cccccccc-1111-2222-3333-444444444444",
  status: "PENDING_PAYMENT",
};

describe("OrdersPage rol-bazli aksiyonlar", () => {
  beforeEach(() => {
    getMock.mockReset();
    postMock.mockReset();
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

  it("FULFILLED sipariste 'Iptal' gorunmez (terminal durum, G5)", async () => {
    setRole(["CSR"]);
    renderWithProviders(<OrdersPage />);
    await screen.findByText("FULFILLED");
    expect(screen.queryByRole("button", { name: /Iptal/ })).not.toBeInTheDocument();
  });

  it("PENDING_PAYMENT sipariste 'Iptal' Popconfirm'den gecerek POST .../cancel cagirir (G5)", async () => {
    setRole(["CSR"]);
    getMock.mockImplementation((url: string) =>
      Promise.resolve(url === "/api/orders" ? page([PENDING_ORDER]) : page([])),
    );
    postMock.mockResolvedValue({
      data: { success: true, data: PENDING_ORDER, message: "Siparis iptal edildi" },
    });
    const user = userEvent.setup();
    renderWithProviders(<OrdersPage />);
    await screen.findByText("PENDING_PAYMENT");

    // Tetikleyici adi ikon yuzunden "close-circle Iptal"; Popconfirm onayi ikonsuz "Iptal et".
    await user.click(screen.getByRole("button", { name: /Iptal/ }));
    await user.click(await screen.findByRole("button", { name: "Iptal et" }));
    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith(`/api/orders/${PENDING_ORDER.orderId}/cancel`),
    );
    expect(await screen.findByText("Siparis iptal edildi")).toBeInTheDocument();
  });
});
