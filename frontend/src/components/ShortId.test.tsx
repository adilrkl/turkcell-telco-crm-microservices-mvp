import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ShortId } from "./ShortId";

describe("ShortId", () => {
  it("deger yoksa '-' gosterir", () => {
    const { container } = render(<ShortId value={null} />);
    expect(container.textContent).toBe("-");
  });

  it("bos string de '-' gosterir", () => {
    const { container } = render(<ShortId value="" />);
    expect(container.textContent).toBe("-");
  });

  it("UUID'yi ilk 8 karaktere kisaltip … ekler", () => {
    render(<ShortId value="11111111-2222-3333-4444-555555555555" />);
    expect(screen.getByText("11111111…")).toBeInTheDocument();
  });
});
