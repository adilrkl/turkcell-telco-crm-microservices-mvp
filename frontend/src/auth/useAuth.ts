import { useQuery } from "@tanstack/react-query";
import { api } from "../lib/axios";
import type { MeResponse, Role } from "../api/types";

/**
 * Oturum durumu + roller (BFF GET /api/me).
 * 401 = giris yapilmamis (hata degil, "user: null" durumu).
 */
export function useAuth() {
  const { data, isLoading } = useQuery({
    queryKey: ["me"],
    queryFn: async () => {
      const res = await api.get<MeResponse>("/api/me");
      return res.data;
    },
    retry: false,
  });

  const user = data ?? null;
  const hasRole = (...roles: Role[]) =>
    user != null && roles.some((r) => user.roles.includes(r));

  return { user, isLoading, hasRole };
}
