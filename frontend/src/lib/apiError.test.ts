import { describe, it, expect } from "vitest";
import { AxiosError } from "axios";
import { apiErrorMessage } from "./apiError";

describe("apiErrorMessage", () => {
  it("backend ApiResponse.message'ini one cikarir", () => {
    const err = new AxiosError("Request failed");
    err.response = {
      data: { success: false, message: "Gecersiz TCKN", errorCode: "CUSTOMER_INVALID" },
      status: 422,
      statusText: "Unprocessable Entity",
      headers: {},
      config: {} as never,
    };
    expect(apiErrorMessage(err, "fallback")).toBe("Gecersiz TCKN");
  });

  it("message yoksa fallback doner (AxiosError ama govde bos)", () => {
    const err = new AxiosError("Network Error");
    expect(apiErrorMessage(err, "Siparis olusturulamadi")).toBe("Siparis olusturulamadi");
  });

  it("response.data.message bos string ise fallback doner", () => {
    const err = new AxiosError("x");
    err.response = {
      data: { success: false, message: "" },
      status: 500,
      statusText: "",
      headers: {},
      config: {} as never,
    };
    expect(apiErrorMessage(err, "fallback")).toBe("fallback");
  });

  it("AxiosError olmayan hatalarda fallback doner", () => {
    expect(apiErrorMessage(new Error("boom"), "fallback")).toBe("fallback");
    expect(apiErrorMessage("string hata", "fallback")).toBe("fallback");
    expect(apiErrorMessage(null, "fallback")).toBe("fallback");
  });
});
