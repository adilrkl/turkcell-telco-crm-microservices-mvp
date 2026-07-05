import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "../../test/utils";

const { getMock, postMock } = vi.hoisted(() => ({ getMock: vi.fn(), postMock: vi.fn() }));
vi.mock("../../lib/axios", () => ({ api: { get: getMock, post: postMock } }));

const { useAuthMock } = vi.hoisted(() => ({ useAuthMock: vi.fn() }));
vi.mock("../../auth/useAuth", () => ({ useAuth: useAuthMock }));

import { CustomerKycDrawer } from "./CustomerKycDrawer";

const CUSTOMER = {
  id: "11111111-2222-3333-4444-555555555555",
  type: "INDIVIDUAL",
  firstName: "Ahmet",
  lastName: "Yilmaz",
  identityNumber: "10000000146",
  dateOfBirth: null,
  status: "PENDING",
};
const DOC = { id: "d1", customerId: CUSTOMER.id, type: "ID_CARD", fileRef: "ref-abcdef12", verifiedAt: null };

function setAuth(roles: string[]) {
  useAuthMock.mockReturnValue({
    user: { username: "u", email: null, fullName: null, roles },
    isLoading: false,
    hasRole: (...r: string[]) => r.some((x) => roles.includes(x)),
  });
}

function setDocs(docs: unknown[]) {
  getMock.mockImplementation((url: string) => {
    if (url.endsWith("/documents")) return Promise.resolve({ data: { data: docs } });
    return Promise.resolve({ data: { data: [] } });
  });
}

describe("CustomerKycDrawer (G3 KYC)", () => {
  beforeEach(() => {
    getMock.mockReset();
    postMock.mockReset();
    useAuthMock.mockReset();
  });

  it("CSR (ADMIN degil) belge listesini gorur ama onay/red butonlarini gormez", async () => {
    setAuth(["CSR"]);
    setDocs([DOC]);
    renderWithProviders(<CustomerKycDrawer customer={CUSTOMER} onClose={vi.fn()} />);

    expect(await screen.findByText("Belgeler (1)")).toBeInTheDocument();
    expect(screen.getByText(/yalniz ADMIN rolunde gorunur/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /KYC onayla/i })).not.toBeInTheDocument();
  });

  it("ADMIN + PENDING + >=1 belge: 'KYC onayla' aktif, Popconfirm'den POST .../kyc/approve", async () => {
    setAuth(["ADMIN"]);
    setDocs([DOC]);
    postMock.mockResolvedValue({ data: { success: true, data: CUSTOMER, message: "KYC onaylandi" } });
    const user = userEvent.setup();
    renderWithProviders(<CustomerKycDrawer customer={CUSTOMER} onClose={vi.fn()} />);
    await screen.findByText("Belgeler (1)");

    const approveBtn = screen.getByRole("button", { name: /KYC onayla/i });
    expect(approveBtn).toBeEnabled();
    await user.click(approveBtn);
    await user.click(await screen.findByRole("button", { name: "Onayla" }));
    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith(`/api/customers/${CUSTOMER.id}/kyc/approve`),
    );
  });

  it("ADMIN + PENDING + belge yok: 'KYC onayla' pasif + uyari gosterir", async () => {
    setAuth(["ADMIN"]);
    setDocs([]);
    renderWithProviders(<CustomerKycDrawer customer={CUSTOMER} onClose={vi.fn()} />);
    await screen.findByText("Belgeler (0)");

    expect(screen.getByRole("button", { name: /KYC onayla/i })).toBeDisabled();
    expect(screen.getByText(/en az bir belge gerekir/i)).toBeInTheDocument();
  });

  it("mock belge yukleme POST .../documents cagirir", async () => {
    setAuth(["CSR"]);
    setDocs([]);
    postMock.mockResolvedValue({ data: { success: true, data: DOC, message: "Belge yuklendi" } });
    const user = userEvent.setup();
    renderWithProviders(<CustomerKycDrawer customer={CUSTOMER} onClose={vi.fn()} />);
    await screen.findByText("Belgeler (0)");

    await user.type(screen.getByPlaceholderText(/kimlik.pdf/i), "pasaport.pdf");
    await user.click(screen.getByRole("button", { name: /Belge ekle/i }));
    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith(
        `/api/customers/${CUSTOMER.id}/documents`,
        expect.objectContaining({ fileName: "pasaport.pdf", type: "ID_CARD" }),
      ),
    );
  });
});
