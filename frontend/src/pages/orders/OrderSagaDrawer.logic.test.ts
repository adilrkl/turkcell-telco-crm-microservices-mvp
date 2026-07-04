import { describe, it, expect } from "vitest";
import { buildStepsView, TERMINAL_STATUSES, ORDER_STATUS_COLOR } from "./OrderSagaDrawer";

describe("buildStepsView (saga adim gorunumu)", () => {
  it("PENDING_PAYMENT -> odeme adimi surecte", () => {
    const v = buildStepsView("PENDING_PAYMENT", null);
    expect(v.current).toBe(1);
    expect(v.status).toBe("process");
    expect(v.descriptions[1]).toContain("rezervasyonu");
  });

  it("PAID -> aktivasyon adimi surecte", () => {
    const v = buildStepsView("PAID", "PENDING_PAYMENT");
    expect(v.current).toBe(2);
    expect(v.status).toBe("process");
    expect(v.descriptions[2]).toContain("Aktivasyon");
  });

  it("FULFILLED -> tamamlandi (finish)", () => {
    const v = buildStepsView("FULFILLED", "PAID");
    expect(v.current).toBe(3);
    expect(v.status).toBe("finish");
    expect(v.descriptions.every((d) => d === undefined)).toBe(true);
  });

  it("CANCELLED + lastActive=PAID -> aktivasyon adiminda hata", () => {
    const v = buildStepsView("CANCELLED", "PAID");
    expect(v.current).toBe(2);
    expect(v.status).toBe("error");
    expect(v.descriptions[2]).toContain("Aktivasyon adiminda iptal");
  });

  it("CANCELLED + lastActive=PENDING_PAYMENT -> odeme adiminda hata", () => {
    const v = buildStepsView("CANCELLED", "PENDING_PAYMENT");
    expect(v.current).toBe(1);
    expect(v.status).toBe("error");
    expect(v.descriptions[1]).toContain("Odeme / numara rezervasyonu");
  });

  it("CANCELLED + lastActive=null -> genel iptal (asama bilinmiyor)", () => {
    const v = buildStepsView("CANCELLED", null);
    expect(v.current).toBe(1);
    expect(v.status).toBe("error");
    expect(v.descriptions[1]).toContain("asama bilgisi API'de sunulmuyor");
  });

  it("bilinmeyen status -> guvenli default (ilk adim, process)", () => {
    const v = buildStepsView("WHATEVER", null);
    expect(v.current).toBe(0);
    expect(v.status).toBe("process");
  });
});

describe("saga sabitleri", () => {
  it("TERMINAL_STATUSES tam olarak FULFILLED + CANCELLED", () => {
    expect(TERMINAL_STATUSES).toEqual(["FULFILLED", "CANCELLED"]);
  });

  it("ORDER_STATUS_COLOR tum durumlar icin renk tanimlar", () => {
    expect(ORDER_STATUS_COLOR.PENDING_PAYMENT).toBe("gold");
    expect(ORDER_STATUS_COLOR.FULFILLED).toBe("green");
    expect(ORDER_STATUS_COLOR.CANCELLED).toBe("red");
  });
});
