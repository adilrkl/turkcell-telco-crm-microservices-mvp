import type { ReactElement, ReactNode } from "react";
import { render } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { App as AntApp, ConfigProvider } from "antd";
import { MemoryRouter } from "react-router-dom";

/** Testlerde retry kapali, izole QueryClient (her test kendi cache'i). */
export function makeQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  });
}

interface Options {
  route?: string;
  client?: QueryClient;
}

/** Uygulamanin saglayici yiginiyla (AntD App + Query + Router) bilesen render eder. */
export function renderWithProviders(ui: ReactElement, options: Options = {}) {
  const { route = "/", client = makeQueryClient() } = options;
  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <ConfigProvider>
        <AntApp>
          <QueryClientProvider client={client}>
            <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
          </QueryClientProvider>
        </AntApp>
      </ConfigProvider>
    );
  }
  return { client, ...render(ui, { wrapper: Wrapper }) };
}
