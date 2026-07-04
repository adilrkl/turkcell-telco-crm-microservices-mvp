import axios, { AxiosError } from "axios";
import { loginRedirect } from "../auth/login";

/**
 * BFF ile same-origin konusan axios instance'i (FRONTEND.md §6/§7).
 * - withCredentials: session cookie (JSESSIONID) her istekte gider.
 * - CSRF: axios, XSRF-TOKEN cookie'sini otomatik X-XSRF-TOKEN header'ina koyar
 *   (BFF'in CookieCsrfTokenRepository adlariyla birebir ayni default'lar).
 * - 401: session dusmus -> login redirect. /api/me haric (login durumunu
 *   sorgulayan cagri; 401'i useAuth "giris yapilmamis" olarak yorumlar).
 */
export const api = axios.create({
  withCredentials: true,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
});

/**
 * Response error interceptor mantigi (test edilebilir olsun diye ayri fonksiyon):
 * /api/me disindaki bir 401'de login'e yonlendirir, hatayi her durumda reject eder.
 */
export function onResponseError(error: AxiosError): Promise<never> {
  const status = error.response?.status;
  const url: string = error.config?.url ?? "";
  if (status === 401 && !url.startsWith("/api/me")) {
    loginRedirect();
  }
  return Promise.reject(error);
}

api.interceptors.response.use((response) => response, onResponseError);
