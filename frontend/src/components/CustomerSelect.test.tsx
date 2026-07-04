import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "../test/utils";

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }));
vi.mock("../lib/axios", () => ({ api: { get: getMock } }));

import { CustomerSelect } from "./CustomerSelect";

function pageOf(content: unknown[]) {
  return { data: { data: { content, number: 0, size: 20, totalElements: content.length } } };
}

describe("CustomerSelect", () => {
  beforeEach(() => getMock.mockReset());

  it("musteri listesini cekip 'Ad Soyad (kisa-id…)' etiketiyle secenek gosterir", async () => {
    getMock.mockResolvedValue(
      pageOf([{ id: "11111111-2222-3333-4444-555555555555", firstName: "Ahmet", lastName: "Yilmaz", status: "ACTIVE" }]),
    );
    const user = userEvent.setup();
    renderWithProviders(<CustomerSelect />);

    await user.click(screen.getByRole("combobox"));
    expect(await screen.findByText("Ahmet Yilmaz (11111111…)")).toBeInTheDocument();
    // GET /api/customers cagrildi
    expect(getMock).toHaveBeenCalledWith("/api/customers", expect.objectContaining({
      params: expect.objectContaining({ page: 0, size: 20 }),
    }));
  });

  it("secim yapinca onChange secilen id ile tetiklenir", async () => {
    getMock.mockResolvedValue(
      pageOf([{ id: "11111111-2222-3333-4444-555555555555", firstName: "Ahmet", lastName: "Yilmaz", status: "ACTIVE" }]),
    );
    const onChange = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<CustomerSelect onChange={onChange} />);

    await user.click(screen.getByRole("combobox"));
    await user.click(await screen.findByText("Ahmet Yilmaz (11111111…)"));
    expect(onChange).toHaveBeenCalledWith("11111111-2222-3333-4444-555555555555");
  });
});
