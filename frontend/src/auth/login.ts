import { api } from "../lib/axios";

/** BFF login akisini baslatir (Authorization Code -> Keycloak). Tam sayfa yonlendirme sarttir. */
export function loginRedirect(): void {
  window.location.href = "/oauth2/authorization/keycloak";
}

/**
 * RP-Initiated logout: BFF, Keycloak oturumunu da sonlandirir.
 * SecurityConfig fetch-dostu davranir: 302 yerine 200 + Location header'i doner;
 * donen URL'e tam sayfa gidilir (Keycloak end-session -> geri redirect).
 */
export async function logout(): Promise<void> {
  const response = await api.post("/logout");
  const location = response.headers["location"] as string | undefined;
  window.location.href = location ?? "/";
}
