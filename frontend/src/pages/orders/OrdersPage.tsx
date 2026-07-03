import { useState } from "react";
import { App, Button, Card, Form, Modal, Select, Space, Table, Tag } from "antd";
import { EyeOutlined, PlusOutlined } from "@ant-design/icons";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../../lib/axios";
import { apiErrorMessage } from "../../lib/apiError";
import { useAuth } from "../../auth/useAuth";
import { ShortId } from "../../components/ShortId";
import { CustomerSelect } from "../../components/CustomerSelect";
import { ORDER_STATUS_COLOR, OrderSagaDrawer } from "./OrderSagaDrawer";
import type { ApiResponse, OrderResponse, RestPage, TariffResponse } from "../../api/types";

const ORDER_STATUSES = ["PENDING_PAYMENT", "PAID", "FULFILLED", "CANCELLED"];

interface OrderFormValues {
  customerId: string;
  tariffCode: string;
}

/**
 * Siparisler (CSR/ADMIN): server-side sayfali liste + siparis olusturma (yalnizca CSR)
 * + saga durum takibi (OrderSagaDrawer, polling). GET /api/orders/{id} yalnizca
 * CUSTOMER/CSR oldugu icin "Izle" da CSR'a kosulludur.
 */
export function OrdersPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { hasRole } = useAuth();
  const canPlaceAndWatch = hasRole("CSR");

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [status, setStatus] = useState<string | undefined>();
  const [customerId, setCustomerId] = useState<string | undefined>();
  const [modalOpen, setModalOpen] = useState(false);
  const [trackedOrderId, setTrackedOrderId] = useState<string | null>(null);
  const [form] = Form.useForm<OrderFormValues>();

  const { data, isFetching } = useQuery({
    queryKey: ["orders", page, pageSize, status, customerId],
    queryFn: async () => {
      const res = await api.get<ApiResponse<RestPage<OrderResponse>>>("/api/orders", {
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

  // Tarife secenekleri: buyuk tek sayfa cekilir, ACTIVE olanlar client-side filtrelenir.
  const { data: tariffs, isFetching: tariffsFetching } = useQuery({
    queryKey: ["tariffs", "select"],
    queryFn: async () => {
      const res = await api.get<ApiResponse<RestPage<TariffResponse>>>("/api/catalog/tariffs", {
        params: { page: 0, size: 100 },
      });
      return res.data.data!;
    },
    enabled: modalOpen,
  });

  const placeOrder = useMutation({
    mutationFn: (values: OrderFormValues) =>
      api.post<ApiResponse<OrderResponse>>("/api/orders", values),
    onSuccess: (res) => {
      message.success(res.data.message ?? "Siparis alindi");
      setModalOpen(false);
      void queryClient.invalidateQueries({ queryKey: ["orders"] });
      // Yeni siparisin sagasini hemen izlemeye basla.
      const created = res.data.data;
      if (created) {
        setTrackedOrderId(created.orderId);
      }
    },
    onError: (error) => message.error(apiErrorMessage(error, "Siparis olusturulamadi")),
  });

  const openCreate = () => {
    form.resetFields();
    setModalOpen(true);
  };

  return (
    <Card
      title="Siparisler"
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
            options={ORDER_STATUSES.map((s) => ({ value: s, label: s }))}
          />
          {canPlaceAndWatch && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              Yeni siparis
            </Button>
          )}
        </Space>
      }
    >
      <Table<OrderResponse>
        rowKey="orderId"
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
          {
            title: "Siparis",
            dataIndex: "orderId",
            render: (v: string) => <ShortId value={v} />,
          },
          {
            title: "Musteri",
            dataIndex: "customerId",
            render: (v: string) => <ShortId value={v} />,
          },
          { title: "Tarife", dataIndex: "tariffCode" },
          {
            title: "Tutar",
            dataIndex: "totalAmount",
            render: (_, record) => `${record.totalAmount} ${record.currency}`,
          },
          {
            title: "Durum",
            dataIndex: "status",
            render: (s: string) => <Tag color={ORDER_STATUS_COLOR[s] ?? "default"}>{s}</Tag>,
          },
          ...(canPlaceAndWatch
            ? [
                {
                  title: "",
                  key: "actions",
                  render: (_: unknown, record: OrderResponse) => (
                    <Button
                      size="small"
                      icon={<EyeOutlined />}
                      onClick={() => setTrackedOrderId(record.orderId)}
                    >
                      Izle
                    </Button>
                  ),
                },
              ]
            : []),
        ]}
      />

      <Modal
        title="Yeni siparis"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        okText="Siparis ver"
        confirmLoading={placeOrder.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(values) => placeOrder.mutate(values)}>
          <Form.Item
            name="customerId"
            label="Musteri"
            rules={[{ required: true, message: "Musteri secin" }]}
          >
            <CustomerSelect />
          </Form.Item>
          <Form.Item
            name="tariffCode"
            label="Tarife"
            rules={[{ required: true, message: "Tarife secin" }]}
            extra="MSISDN saga icinde otomatik rezerve edilir."
          >
            <Select
              loading={tariffsFetching}
              placeholder="Tarife sec"
              showSearch
              optionFilterProp="label"
              options={(tariffs?.content ?? [])
                .filter((t) => !t.status || t.status === "ACTIVE")
                .map((t) => ({
                  value: t.code,
                  label: `${t.name} — ${t.code} (${t.monthlyFee} TRY/ay)`,
                }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      <OrderSagaDrawer orderId={trackedOrderId} onClose={() => setTrackedOrderId(null)} />
    </Card>
  );
}
