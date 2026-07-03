import { Routes, Route } from "react-router-dom";
import { AppLayout } from "./layout/AppLayout";
import { RequireRole } from "./auth/RequireRole";
import { DashboardPage } from "./pages/DashboardPage";
import { CustomersPage } from "./pages/customers/CustomersPage";
import { TicketsPage } from "./pages/tickets/TicketsPage";
import { TicketDetailPage } from "./pages/tickets/TicketDetailPage";
import { OrdersPage } from "./pages/orders/OrdersPage";
import { PageStub } from "./components/PageStub";

export function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<DashboardPage />} />
        <Route
          path="/customers"
          element={
            <RequireRole roles={["CSR", "ADMIN"]}>
              <CustomersPage />
            </RequireRole>
          }
        />
        <Route
          path="/tickets"
          element={
            <RequireRole roles={["CSR", "ADMIN"]}>
              <TicketsPage />
            </RequireRole>
          }
        />
        <Route
          path="/tickets/:id"
          element={
            <RequireRole roles={["CSR", "ADMIN"]}>
              <TicketDetailPage />
            </RequireRole>
          }
        />
        <Route
          path="/orders"
          element={
            <RequireRole roles={["CSR", "ADMIN"]}>
              <OrdersPage />
            </RequireRole>
          }
        />
        <Route path="/tariffs" element={<PageStub title="Tarifeler" sprint="Sprint 3: katalog listesi + CATALOG_ADMIN tarife olusturma" />} />
        <Route path="/subscriptions" element={<PageStub title="Abonelikler" sprint="Sprint 4: abonelik listesi (musteri filtresi)" />} />
        <Route path="/billing" element={<PageStub title="Faturalar" sprint="Sprint 4: fatura listesi + kalem detayi (BILLING_ADMIN)" />} />
      </Route>
    </Routes>
  );
}
