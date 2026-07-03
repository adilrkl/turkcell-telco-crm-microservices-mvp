import { Layout, Menu, Space, Tag, Typography, Button } from "antd";
import {
  TeamOutlined,
  ShoppingCartOutlined,
  TagsOutlined,
  FileTextOutlined,
  MobileOutlined,
  CustomerServiceOutlined,
  HomeOutlined,
  LogoutOutlined,
} from "@ant-design/icons";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { logout } from "../auth/login";
import type { Role } from "../api/types";

const { Header, Sider, Content } = Layout;

/** Menu ogeleri rol-bazli filtrelenir (FRONTEND.md §9) — gercek yetki backend'de. */
const MENU: { key: string; label: string; icon: React.ReactNode; roles: Role[] }[] = [
  { key: "/", label: "Ana Sayfa", icon: <HomeOutlined />, roles: [] },
  { key: "/customers", label: "Musteriler", icon: <TeamOutlined />, roles: ["CSR", "ADMIN"] },
  { key: "/tickets", label: "Destek Talepleri", icon: <CustomerServiceOutlined />, roles: ["CSR", "ADMIN"] },
  { key: "/orders", label: "Siparisler", icon: <ShoppingCartOutlined />, roles: ["CSR", "ADMIN"] },
  { key: "/tariffs", label: "Tarifeler", icon: <TagsOutlined />, roles: [] },
  { key: "/subscriptions", label: "Abonelikler", icon: <MobileOutlined />, roles: ["CSR", "ADMIN"] },
  { key: "/billing", label: "Faturalar", icon: <FileTextOutlined />, roles: ["BILLING_ADMIN", "CSR", "ADMIN"] },
];

export function AppLayout() {
  const { user, hasRole } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const items = MENU.filter((m) => m.roles.length === 0 || hasRole(...m.roles)).map(
    ({ key, label, icon }) => ({ key, label, icon }),
  );

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Sider breakpoint="lg" collapsedWidth={64}>
        <div style={{ color: "#fff", fontWeight: 700, padding: 16, fontSize: 16 }}>
          Telco CRM
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={items}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: "#fff",
            display: "flex",
            justifyContent: "flex-end",
            alignItems: "center",
            paddingInline: 24,
          }}
        >
          {user && (
            <Space>
              <Typography.Text strong>{user.username}</Typography.Text>
              {user.roles
                .filter((r) => ["CUSTOMER", "CSR", "CATALOG_ADMIN", "BILLING_ADMIN", "ADMIN"].includes(r))
                .map((r) => (
                  <Tag key={r} color="blue">
                    {r}
                  </Tag>
                ))}
              <Button icon={<LogoutOutlined />} onClick={() => void logout()}>
                Cikis
              </Button>
            </Space>
          )}
        </Header>
        <Content style={{ margin: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
