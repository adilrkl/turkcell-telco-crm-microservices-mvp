import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError } from "axios";
import { renderWithProviders } from "../../test/utils";

const { getMock, postMock } = vi.hoisted(() => ({ getMock: vi.fn(), postMock: vi.fn() }));
vi.mock("../../lib/axios", () => ({ api: { get: getMock, post: postMock } }));

import { SubscriptionsPage } from "./SubscriptionsPage";

const BASE = {
  id: "99999999-1111-2222-3333-444444444444",
  orderId: "88888888-1111-2222-3333-444444444444",
  customerId: "77777777-1111-2222-3333-444444444444",
  msisdn: "5551234567",
  tariffCode: "TARIFE_M",
  activatedAt: "2026-06-01T09:00:00Z",
  suspendedAt: null,
  terminatedAt: null,
};

function page(content: unknown[]) {
  return { data: { data: { content, number: 0, size: 10, totalElements: content.length } } };
}

const QUOTA = {
  subscriptionId: BASE.id,
  periodStart: "2026-07-01",
  periodEnd: "2026-07-31",
  items: [
    { type: "VOICE", total: 1000, remaining: 400, usedPct: 60 },
    { type: "DATA", total: 20480, remaining: 0, usedPct: 100 },
  ],
};

function setup(sub: Record<string, unknown>) {
  getMock.mockImplementation((url: string) => {
    if (url === "/api/subscriptions") return Promise.resolve(page([sub]));
    if (url === "/api/customers") return Promise.resolve(page([]));
    if (url === "/api/usage/quota") return Promise.resolve({ data: { data: QUOTA } });
    return Promise.resolve(page([]));
  });
}

describe("SubscriptionsPage yasam dongusu aksiyonlari (G4)", () => {
  beforeEach(() => {
    getMock.mockReset();
    postMock.mockReset();
    postMock.mockResolvedValue({ data: { success: true, data: BASE, message: "Islem tamamlandi" } });
  });

  it("ACTIVE abonelikte 'Askiya al' ve 'Sonlandir' gorunur, 'Yeniden aktive et' gizli", async () => {
    setup({ ...BASE, status: "ACTIVE" });
    renderWithProviders(<SubscriptionsPage />);
    await screen.findByText("5551234567");
    expect(screen.getByRole("button", { name: /Askiya al/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Sonlandir/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Yeniden aktive et/i })).not.toBeInTheDocument();
  });

  it("TERMINATED abonelikte hicbir yasam dongusu aksiyonu gorunmez", async () => {
    setup({ ...BASE, status: "TERMINATED", terminatedAt: "2026-06-10T09:00:00Z" });
    renderWithProviders(<SubscriptionsPage />);
    await screen.findByText("5551234567");
    expect(screen.queryByRole("button", { name: /Askiya al/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Sonlandir/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Yeniden aktive et/i })).not.toBeInTheDocument();
  });

  it("SUSPENDED abonelikte 'Yeniden aktive et' POST .../reactivate cagirir", async () => {
    setup({ ...BASE, status: "SUSPENDED", suspendedAt: "2026-06-05T09:00:00Z" });
    const user = userEvent.setup();
    renderWithProviders(<SubscriptionsPage />);
    await screen.findByText("5551234567");

    await user.click(screen.getByRole("button", { name: /Yeniden aktive et/i }));
    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith(`/api/subscriptions/${BASE.id}/reactivate`),
    );
    expect(await screen.findByText("Islem tamamlandi")).toBeInTheDocument();
  });

  it("gecersiz gecis (409) backend mesajini yuzeye cikarir", async () => {
    const err = new AxiosError("Request failed");
    err.response = {
      data: { success: false, message: "Gecersiz gecis: TERMINATED", errorCode: "SUBSCRIPTION_INVALID_STATE" },
      status: 409,
      statusText: "",
      headers: {},
      config: {} as never,
    };
    postMock.mockRejectedValue(err);
    setup({ ...BASE, status: "SUSPENDED", suspendedAt: "2026-06-05T09:00:00Z" });
    const user = userEvent.setup();
    renderWithProviders(<SubscriptionsPage />);
    await screen.findByText("5551234567");

    await user.click(screen.getByRole("button", { name: /Yeniden aktive et/i }));
    expect(await screen.findByText("Gecersiz gecis: TERMINATED")).toBeInTheDocument();
  });

  it("ACTIVE abonelikte 'Kota' drawer'i GET /usage/quota ile acar (G1)", async () => {
    setup({ ...BASE, status: "ACTIVE" });
    const user = userEvent.setup();
    renderWithProviders(<SubscriptionsPage />);
    await screen.findByText("5551234567");

    await user.click(screen.getByRole("button", { name: /Kota/i }));
    expect(await screen.findByText("VOICE")).toBeInTheDocument();
    expect(screen.getByText("DATA")).toBeInTheDocument();
    expect(screen.getByText(/400 \/ 1000 dk kaldi/i)).toBeInTheDocument();
    await waitFor(() =>
      expect(getMock).toHaveBeenCalledWith(
        "/api/usage/quota",
        expect.objectContaining({ params: { subscriptionId: BASE.id } }),
      ),
    );
  });

  it("TERMINATED abonelikte 'Kota' butonu gorunmez", async () => {
    setup({ ...BASE, status: "TERMINATED", terminatedAt: "2026-06-10T09:00:00Z" });
    renderWithProviders(<SubscriptionsPage />);
    await screen.findByText("5551234567");
    expect(screen.queryByRole("button", { name: /Kota/i })).not.toBeInTheDocument();
  });

  it("'Sonlandir' onay kutusundan gecerek POST .../terminate cagirir", async () => {
    setup({ ...BASE, status: "ACTIVE" });
    const user = userEvent.setup();
    renderWithProviders(<SubscriptionsPage />);
    await screen.findByText("5551234567");

    await user.click(screen.getByRole("button", { name: /Sonlandir/i }));
    // Popconfirm acilir: yikici islem uyarisi + onay butonu
    expect(await screen.findByText(/MSISDN havuza doner/i)).toBeInTheDocument();

    const confirmButtons = screen.getAllByRole("button", { name: /Sonlandir/i });
    await user.click(confirmButtons[confirmButtons.length - 1]);
    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith(`/api/subscriptions/${BASE.id}/terminate`),
    );
  });
});
