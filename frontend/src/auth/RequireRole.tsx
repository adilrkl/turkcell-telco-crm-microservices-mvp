import type { ReactNode } from "react";
import { Button, Result, Spin } from "antd";
import { useAuth } from "./useAuth";
import { loginRedirect } from "./login";
import type { Role } from "../api/types";

/**
 * Rol bazli route guard (FRONTEND.md §9). Yalnizca UX icindir —
 * asil yetki her zaman backend'dedir (@PreAuthorize + JWT).
 */
export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user, isLoading, hasRole } = useAuth();

  if (isLoading) {
    return <Spin style={{ display: "block", margin: "120px auto" }} size="large" />;
  }
  if (!user) {
    return (
      <Result
        status="info"
        title="Oturum acilmamis"
        subTitle="Devam etmek icin Keycloak ile giris yapin."
        extra={
          <Button type="primary" onClick={loginRedirect}>
            Giris yap
          </Button>
        }
      />
    );
  }
  if (!hasRole(...roles)) {
    return (
      <Result
        status="403"
        title="403"
        subTitle="Bu sayfa icin yetkiniz yok."
      />
    );
  }
  return <>{children}</>;
}
