import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError } from "axios";
import { renderWithProviders } from "../../test/utils";

const { getMock, postMock } = vi.hoisted(() => ({ getMock: vi.fn(), postMock: vi.fn() }));
vi.mock("../../lib/axios", () => ({ api: { get: getMock, post: postMock, put: vi.fn() } }));

import { CustomersPage } from "./CustomersPage";

const CUSTOMER = {
  id: "11111111-2222-3333-4444-555555555555",
  type: "INDIVIDUAL",
  firstName: "Ahmet",
  lastName: "Yilmaz",
  identityNumber: "10000000146",
  dateOfBirth: null,
  status: "ACTIVE",
};

function listResponse(content: unknown[]) {
  return { data: { data: { content, number: 0, size: 10, totalElements: content.length } } };
}

describe("CustomersPage", () => {
  beforeEach(() => {
    getMock.mockReset();
    postMock.mockReset();
    getMock.mockResolvedValue(listResponse([CUSTOMER]));
  });

  it("musteri listesini render eder", async () => {
    renderWithProviders(<CustomersPage />);
    expect(await screen.findByText("Ahmet")).toBeInTheDocument();
    expect(screen.getByText("10000000146")).toBeInTheDocument();
  });

  it("olustur modalinda kimlik etiketi tip'e gore TCKN/VKN olur (G9)", async () => {
    const user = userEvent.setup();
    renderWithProviders(<CustomersPage />);
    await screen.findByText("Ahmet");

    await user.click(screen.getByRole("button", { name: /Yeni musteri/i }));
    // Varsayilan INDIVIDUAL -> TCKN etiketi
    expect(await screen.findByText("TCKN")).toBeInTheDocument();

    // Tip'i Kurumsal yap -> etiket VKN'e doner ("Tip" label'i tip Select'ini hedefler)
    await user.click(screen.getByLabelText("Tip"));
    await user.click(await screen.findByText("Kurumsal"));
    expect(await screen.findByText("VKN")).toBeInTheDocument();
  });

  it("backend 422 dogrulama mesajini (Gecersiz TCKN) yuzeye cikarir", async () => {
    const err = new AxiosError("Request failed");
    err.response = {
      data: { success: false, message: "Gecersiz TCKN", errorCode: "CUSTOMER_INVALID" },
      status: 422,
      statusText: "",
      headers: {},
      config: {} as never,
    };
    postMock.mockRejectedValue(err);

    const user = userEvent.setup();
    renderWithProviders(<CustomersPage />);
    await screen.findByText("Ahmet");

    await user.click(screen.getByRole("button", { name: /Yeni musteri/i }));
    await screen.findByText("TCKN");

    // Zorunlu alanlari doldur (Ad / Soyad / TCKN) — AntD label htmlFor ile eslesir.
    await user.type(screen.getByLabelText("Ad"), "Veli");
    await user.type(screen.getByLabelText("Soyad"), "Kaya");
    await user.type(screen.getByLabelText("TCKN"), "12345678901");
    await user.click(screen.getByRole("button", { name: /^OK$|Tamam/i }));

    // apiErrorMessage sayesinde generic degil, backend mesaji gorunur
    expect(await screen.findByText("Gecersiz TCKN")).toBeInTheDocument();
    await waitFor(() => expect(postMock).toHaveBeenCalled());
  });
});
