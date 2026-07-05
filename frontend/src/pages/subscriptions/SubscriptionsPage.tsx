import { useState } from "react";
import { App, Button, Card, Popconfirm, Select, Space, Table, Tag } from "antd";
import {
  BarChartOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  StopOutlined,
} from "@ant-design/icons";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import dayjs from "dayjs";
import { api } from "../../lib/axios";
import { apiErrorMessage } from "../../lib/apiError";
import { ShortId } from "../../components/ShortId";
import { CustomerSelect } from "../../components/CustomerSelect";
import { SubscriptionQuotaDrawer } from "./SubscriptionQuotaDrawer";
import type { ApiResponse, RestPage, SubscriptionResponse } from "../../api/types";

const SUBSCRIPTION_STATUSES = ["PENDING", "ACTIVE", "SUSPENDED", "TERMINATED"];

/** subscriptions durumlari (Subscription.java): PENDING -> ACTIVE <-> SUSPENDED, ACTIVE/SUSPENDED -> TERMINATED. */
export const SUBSCRIPTION_STATUS_COLOR: Record<string, string> = {
  PENDING: "gold",
  ACTIVE: "green",
  SUSPENDED: "orange",
  TERMINATED: "red",
};

type LifecycleAction = "suspend" | "reactivate" | "terminate";

/**
 * Abonelikler (CSR/ADMIN): server-side sayfali liste + customerId/status filtreleri
 * + yasam dongusu aksiyonlari (G4, FR-14): suspend / reactivate / terminate.
 * Gecersiz gecis backend'de 409 SUBSCRIPTION_INVALID_STATE doner; mesaji yuzeye cikar.
 */
export function SubscriptionsPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [status, setStatus] = useState<string | undefined>();
  const [customerId, setCustomerId] = useState<string | undefined>();
  const [quotaSubId, setQuotaSubId] = useState<string | null>(null);

  const { data, isFetching } = useQuery({
    queryKey: ["subscriptions", page, pageSize, status, customerId],
    queryFn: async () => {
      const res = await api.get<ApiResponse<RestPage<SubscriptionResponse>>>("/api/subscriptions", {
        params: {
          page,
          size: pageSize,
          ...(status ? { status } : {}),
          ...(customerId ? { customerId } : {}),
        },
      });
      return res.data.data!;
    },
    placeholderData: keepPreviousData,
  });

  const lifecycle = useMutation({
    mutationFn: ({ id, action }: { id: string; action: LifecycleAction }) =>
      api.post<ApiResponse<SubscriptionResponse>>(`/api/subscriptions/${id}/${action}`),
    onSuccess: (res) => {
      message.success(res.data.message ?? "Islem tamamlandi");
      void queryClient.invalidateQueries({ queryKey: ["subscriptions"] });
    },
    onError: (error) => message.error(apiErrorMessage(error, "Islem basarisiz")),
  });

  const rowBusy = (id: string) => lifecycle.isPending && lifecycle.variables?.id === id;

  return (
    <Card
      title="Abonelikler"
      extra={
        <Space wrap>
          <CustomerSelect
            value={customerId}
            onChange={(v) => {
              setPage(0);
              setCustomerId(v);
            }}
            placeholder="Musteri filtrele"
            style={{ width: 260 }}
            allowClear
          />
          <Select
            placeholder="Durum filtrele"
            allowClear
            style={{ width: 190 }}
            value={status}
            onChange={(v) => {
              setPage(0);
              setStatus(v);
            }}
            options={SUBSCRIPTION_STATUSES.map((s) => ({ value: s, label: s }))}
          />
        </Space>
      }
    >
      <Table<SubscriptionResponse>
        rowKey="id"
        loading={isFetching}
        dataSource={data?.content}
        pagination={{
          current: page + 1,
          pageSize,
          total: data?.totalElements ?? 0,
          showSizeChanger: true,
          onChange: (p, s) => {
            setPage(p - 1);
            setPageSize(s);
          },
        }}
        columns={[
          { title: "Abonelik", dataIndex: "id", render: (v: string) => <ShortId value={v} /> },
          { title: "MSISDN", dataIndex: "msisdn", render: (v: string | null) => v ?? "-" },
          { title: "Tarife", dataIndex: "tariffCode" },
          {
            title: "Musteri",
            dataIndex: "customerId",
            render: (v: string) => <ShortId value={v} />,
          },
          {
            title: "Durum",
            dataIndex: "status",
            render: (s: string) => (
              <Tag color={SUBSCRIPTION_STATUS_COLOR[s] ?? "default"}>{s}</Tag>
            ),
          },
          {
            title: "Aktivasyon",
            dataIndex: "activatedAt",
            render: (v: string | null) => (v ? dayjs(v).format("YYYY-MM-DD HH:mm") : "-"),
          },
          {
            title: "",
            key: "actions",
            render: (_, record) => {
              const busy = rowBusy(record.id);
              return (
                <Space>
                  {(record.status === "ACTIVE" || record.status === "SUSPENDED") && (
                    <Button
                      size="small"
                      icon={<BarChartOutlined />}
                      onClick={() => setQuotaSubId(record.id)}
                    >
                      Kota
                    </Button>
                  )}
                  {record.status === "ACTIVE" && (
                    <Popconfirm
                      title="Abonelik askiya alinsin mi?"
                      okText="Askiya al"
                      cancelText="Vazgec"
                      onConfirm={() => lifecycle.mutate({ id: record.id, action: "suspend" })}
                    >
                      <Button size="small" icon={<PauseCircleOutlined />} loading={busy}>
                        Askiya al
                      </Button>
                    </Popconfirm>
                  )}
                  {record.status === "SUSPENDED" && (
                    <Button
                      size="small"
                      icon={<PlayCircleOutlined />}
                      loading={busy}
                      onClick={() => lifecycle.mutate({ id: record.id, action: "reactivate" })}
                    >
                      Yeniden aktive et
                    </Button>
                  )}
                  {(record.status === "ACTIVE" || record.status === "SUSPENDED") && (
                    <Popconfirm
                      title="Abonelik sonlandirilsin mi?"
                      description="MSISDN havuza doner, bu islem geri alinamaz."
                      okText="Sonlandir"
                      okButtonProps={{ danger: true }}
                      cancelText="Vazgec"
                      onConfirm={() => lifecycle.mutate({ id: record.id, action: "terminate" })}
                    >
                      <Button size="small" danger icon={<StopOutlined />} loading={busy}>
                        Sonlandir
                      </Button>
                    </Popconfirm>
                  )}
                </Space>
              );
            },
          },
        ]}
      />

      <SubscriptionQuotaDrawer subscriptionId={quotaSubId} onClose={() => setQuotaSubId(null)} />
    </Card>
  );
}
