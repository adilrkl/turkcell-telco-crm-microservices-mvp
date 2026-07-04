import { describe, it, expect } from "vitest";
import { TRANSITIONS, UUID_RE, transitionLabel } from "./TicketDetailPage";

describe("TRANSITIONS (backend TicketBusinessRules aynasi)", () => {
  it("OPEN -> IN_PROGRESS | CLOSED", () => {
    expect(TRANSITIONS.OPEN).toEqual(["IN_PROGRESS", "CLOSED"]);
  });
  it("IN_PROGRESS -> RESOLVED | CLOSED", () => {
    expect(TRANSITIONS.IN_PROGRESS).toEqual(["RESOLVED", "CLOSED"]);
  });
  it("RESOLVED tekrar acilabilir (IN_PROGRESS) veya kapatilir", () => {
    expect(TRANSITIONS.RESOLVED).toEqual(["IN_PROGRESS", "CLOSED"]);
  });
  it("CLOSED terminaldir (gecis yok)", () => {
    expect(TRANSITIONS.CLOSED).toEqual([]);
  });
});

describe("transitionLabel", () => {
  it("OPEN'dan IN_PROGRESS -> 'Isleme al'", () => {
    expect(transitionLabel("OPEN", "IN_PROGRESS")).toBe("Isleme al");
  });
  it("RESOLVED'dan IN_PROGRESS -> 'Yeniden ac'", () => {
    expect(transitionLabel("RESOLVED", "IN_PROGRESS")).toBe("Yeniden ac");
  });
  it("RESOLVED hedefi -> 'Cozuldu isaretle'", () => {
    expect(transitionLabel("IN_PROGRESS", "RESOLVED")).toBe("Cozuldu isaretle");
  });
  it("CLOSED hedefi -> 'Kapat'", () => {
    expect(transitionLabel("OPEN", "CLOSED")).toBe("Kapat");
  });
});

describe("UUID_RE (Bana ata / Ata UUID dogrulamasi)", () => {
  it("gecerli v4 UUID kabul edilir", () => {
    expect(UUID_RE.test("11111111-1111-1111-1111-111111111111")).toBe(true);
    expect(UUID_RE.test("A1B2C3D4-E5F6-7890-ABCD-EF0123456789")).toBe(true);
  });
  it("gecersiz formatlar reddedilir", () => {
    expect(UUID_RE.test("")).toBe(false);
    expect(UUID_RE.test("not-a-uuid")).toBe(false);
    expect(UUID_RE.test("11111111-1111-1111-1111-11111111111")).toBe(false); // 1 eksik hane
    expect(UUID_RE.test("11111111111111111111111111111111")).toBe(false); // tire yok
    expect(UUID_RE.test("gggggggg-1111-1111-1111-111111111111")).toBe(false); // hex disi
  });
});
