import { Button, Descriptions, Drawer, Progress, Result, Space, Spin, Typography } from "antd";
import { useQuery } from "@tanstack/react-query";
import { api } from "../../lib/axios";
import { ShortId } from "../../components/ShortId";
import type { ApiResponse, QuotaResponse } from "../../api/types";

/** Kota kalemi birimleri (QuotaItem.type): VOICE=dakika, SMS=adet, DATA=MB. */
const UNIT: Record<string, string> = { VOICE: "dk", SMS: "adet", DATA: "MB" };

interface Props {
  subscriptionId: string | null;
  onClose: () => void;
}

/**
 * Aboneligin icinde bulunulan donem kalan kotasi (G1, FR-19):
 * GET /api/usage/quota?subscriptionId= -> tip bazli Progress (kullanim %).
 */
export function SubscriptionQuotaDrawer({ subscriptionId, onClose }: Props) {
  const { data, isFetching, isError, refetch } = useQuery({
    queryKey: ["quota", subscriptionId],
    queryFn: async () => {
      const res = await api.get<ApiResponse<QuotaResponse>>("/api/usage/quota", {
        params: { subscriptionId },
      });
      return res.data.data!;
    },
    enabled: !!subscriptionId,
  });

  return (
    <Drawer title="Kalan kota" width={480} open={!!subscriptionId} onClose={onClose} destroyOnHidden>
      {isError && (
        <Result
          status="warning"
          title="Kota alinamadi"
          extra={
            <Button type="primary" onClick={() => void refetch()}>
              Yeniden dene
            </Button>
          }
        />
      )}
      {!isError && (isFetching || !data) && (
        <Spin style={{ display: "block", margin: "80px auto" }} size="large" />
      )}
      {!isError && !isFetching && data && (
        <Space direction="vertical" size="large" style={{ width: "100%" }}>
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="Abonelik">
              <ShortId value={data.subscriptionId} />
            </Descriptions.Item>
            <Descriptions.Item label="Donem">
              {data.periodStart ?? "-"} — {data.periodEnd ?? "-"}
            </Descriptions.Item>
          </Descriptions>

          {data.items.length === 0 && (
            <Typography.Text type="secondary">Bu donem icin kota kaydi yok.</Typography.Text>
          )}
          {data.items.map((item) => (
            <div key={item.type}>
              <Space style={{ justifyContent: "space-between", width: "100%" }}>
                <Typography.Text strong>{item.type}</Typography.Text>
                <Typography.Text type="secondary">
                  {item.remaining} / {item.total} {UNIT[item.type] ?? ""} kaldi
                </Typography.Text>
              </Space>
              <Progress
                percent={item.usedPct}
                status={item.usedPct >= 100 ? "exception" : "active"}
              />
            </div>
          ))}
        </Space>
      )}
    </Drawer>
  );
}
