import { AxiosError } from "axios";
import type { ApiResponse } from "../api/types";

/**
 * Mutasyon hatalarinda backend'in ApiResponse.message alanini one cikarir
 * (or. 409 TICKET_INVALID_STATE -> "Gecersiz gecis: X -> Y"); yoksa fallback doner.
 */
export function apiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof AxiosError) {
    const data = error.response?.data as ApiResponse<unknown> | undefined;
    if (data?.message) {
      return data.message;
    }
  }
  return fallback;
}
