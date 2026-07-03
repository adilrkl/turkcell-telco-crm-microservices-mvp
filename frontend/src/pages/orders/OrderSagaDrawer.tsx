import { useEffect, useRef } from "react";
import { Descriptions, Drawer, Result, Space, Spin, Steps, Tag, Typography } from "antd";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../../lib/axios";
import { ShortId } from "../../components/ShortId";
import type { ApiResponse, OrderResponse } from "../../api/types";

export const ORDER_STATUS_COLOR: Record<string, string> = {
  PENDING_PAYMENT: "gold",
  PAID: "blue",
  FULFILLED: "green",
  CANCELLED: "red",
};

const TERMINAL_STATUSES = ["FULFILLED", "CANCELLED"];

type StepStatus = "wait" | "process" | "finish" | "error";

interface StepsView {
  current: number;
  status: StepStatus;
  descriptions: (string | undefined)[];
}

/**
 * Saga adim gorunumu yalnizca order.status'tan turetilir (ayrica adim endpoint'i yok).
 * CANCELLED'da hangi adimin patladigi API'de yoktur; canli izlemede gorulen son
 * non-terminal durumdan (PENDING_PAYMENT -> odeme/rezervasyon, PAID -> aktivasyon) cikarilir.
 */
function buildStepsView(status: string, lastActive: string | null): StepsView {
  const none: (string | undefined)[] = [undefined, undefined, undefined, undefined];
  switch (status) {
    case "PENDING_PAYMENT":
      return {
        current: 1,
        status: "process",
        descriptions: [undefined, "Numara rezervasyonu + odeme surecte", undefined, undefined],
      };
    case "PAID":
      return { current: 2, status: "process", descriptions: [undefined, undefined, "Aktivasyon surecte", undefined] };
    case "FULFILLED":
      return { current: 3, status: "finish", descriptions: none };
    case "CANCELLED": {
      if (lastActive === "PAID") {
        return {
          current: 2,
          status: "error",
          descriptions: [undefined, undefined, "Aktivasyon adiminda iptal edildi", undefined],
        };
      }
      if (lastActive === "PENDING_PAYMENT") {
        return {
          current: 1,
          status: "error",
          descriptions: [undefined, "Odeme / numara rezervasyonu adiminda iptal edildi", undefined, undefined],
        };
      }
      return {
        current: 1,
        status: "error",
        descriptions: [undefined, "Iptal edildi (asama bilgisi API'de sunulmuyor)", undefined, undefined],
      };
    }
    default:
      return { current: 0, status: "process", descriptions: none };
  }
}

interface Props {
  orderId: string | null;
  onClose: () => void;
}

/**
 * Siparis saga takibi: 2 sn'de bir GET /api/orders/{id} poll'lar,
 * terminal durumda (FULFILLED/CANCELLED) polling durur ve siparis listesi invalidate edilir.
 */
export function OrderSagaDrawer({ orderId, onClose }: Props) {
  const queryClient = useQueryClient();
  const lastActiveRef = useRef<string | null>(null);

  // Farkli bir siparis izlenmeye baslaninca gecmis durum bilgisini sifirla.
  useEffect(() => {
    lastActiveRef.current = null;
  }, [orderId]);

  const { data: order, isError } = useQuery({
    queryKey: ["order", orderId],
    queryFn: async () => {
      const res = await api.get<ApiResponse<OrderResponse>>(`/api/orders/${orderId}`);
      return res.data.data!;
    },
    enabled: !!orderId,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status && TERMINAL_STATUSES.includes(status) ? false : 2000;
    },
  });

  useEffect(() => {
    if (!order) {
      return;
    }
    if (order.status === "PENDING_PAYMENT" || order.status === "PAID") {
      lastActiveRef.current = order.status;
    }
    if (TERMINAL_STATUSES.includes(order.status)) {
      // Liste sayfasindaki durum kolonu da guncellensin.
      void queryClient.invalidateQueries({ queryKey: ["orders"] });
    }
  }, [order, queryClient]);

  const view = order ? buildStepsView(order.status, lastActiveRef.current) : null;
  const isTerminal = !!order && TERMINAL_STATUSES.includes(order.status);

  return (
    <Drawer title="Siparis takibi" width={480} open={!!orderId} onClose={onClose} destroyOnClose>
      {isError && <Result status="warning" title="Siparis durumu alinamadi" />}
      {!isError && !order && <Spin style={{ display: "block", margin: "80px auto" }} size="large" />}
      {!isError && order && view && (
        <Space direction="vertical" size="large" style={{ width: "100%" }}>
          <Space wrap>
            <Tag color={ORDER_STATUS_COLOR[order.status] ?? "default"} style={{ fontSize: 14, padding: "4px 10px" }}>
              {order.status}
            </Tag>
            {order.status === "CANCELLED" && (
              <Tag color="red" style={{ fontSize: 14, padding: "4px 10px" }}>
                IPTAL EDILDI
              </Tag>
            )}
            {!isTerminal && (
              <Typography.Text type="secondary">Durum 2 sn araliklarla guncelleniyor...</Typography.Text>
            )}
          </Space>

          <Steps
            direction="vertical"
            current={view.current}
            status={view.status}
            items={[
              { title: "Siparis alindi", description: view.descriptions[0] },
              { title: "Odeme", description: view.descriptions[1] },
              { title: "Aktivasyon", description: view.descriptions[2] },
              { title: "Tamamlandi", description: view.descriptions[3] },
            ]}
          />

          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="Siparis">
              <ShortId value={order.orderId} />
            </Descriptions.Item>
            <Descriptions.Item label="Musteri">
              <ShortId value={order.customerId} />
            </Descriptions.Item>
            <Descriptions.Item label="Tarife">{order.tariffCode}</Descriptions.Item>
            <Descriptions.Item label="Tutar">
              {order.totalAmount} {order.currency}
            </Descriptions.Item>
          </Descriptions>
        </Space>
      )}
    </Drawer>
  );
}
