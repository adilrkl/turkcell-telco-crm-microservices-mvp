import { Button, Card, Result, Space, Tag, Typography } from "antd";
import { useAuth } from "../auth/useAuth";
import { loginRedirect } from "../auth/login";

export function DashboardPage() {
  const { user, isLoading } = useAuth();

  if (!isLoading && !user) {
    return (
      <Result
        status="info"
        title="Telco CRM"
        subTitle="Devam etmek icin Keycloak ile giris yapin (BFF oauth2Login)."
        extra={
          <Button type="primary" size="large" onClick={loginRedirect}>
            Giris yap
          </Button>
        }
      />
    );
  }

  return (
    <Card loading={isLoading} title="Hos geldin">
      {user && (
        <Space direction="vertical">
          <Typography.Text>
            <strong>{user.fullName ?? user.username}</strong> ({user.email ?? "-"})
          </Typography.Text>
          <Space wrap>
            {user.roles.map((r) => (
              <Tag key={r}>{r}</Tag>
            ))}
          </Space>
          <Typography.Text type="secondary">
            Soldaki menu rollerine gore filtrelenir; asil yetki backend'dedir (@PreAuthorize).
          </Typography.Text>
        </Space>
      )}
    </Card>
  );
}
