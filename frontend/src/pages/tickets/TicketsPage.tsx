import { useState } from "react";
import { Button, Card, Select, Space, Table, Tag } from "antd";
import { RightOutlined } from "@ant-design/icons";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import { api } from "../../lib/axios";
import { ShortId } from "../../components/ShortId";
import type { ApiResponse, RestPage, TicketResponse } from "../../api/types";

const STATUS_COLOR: Record<string, string> = {
  OPEN: "gold",
  IN_PROGRESS: "blue",
  RESOLVED: "green",
  CLOSED: "default",
};

const PRIORITY_COLOR: Record<string, string> = {
  LOW: "default",
  MEDIUM: "blue",
  HIGH: "orange",
  URGENT: "red",
};

/** Destek talepleri (CSR/ADMIN) — server-side sayfalama + durum filtresi; satirdan detaya gecis. */
export function TicketsPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [status, setStatus] = useState<string | undefined>();

  const { data, isFetching } = useQuery({
    queryKey: ["tickets", page, pageSize, status],
    queryFn: async () => {
      const res = await api.get<ApiResponse<RestPage<TicketResponse>>>("/api/tickets", {
        params: { page, size: pageSize, ...(status ? { status } : {}) },
      });
      return res.data.data!;
    },
    placeholderData: keepPreviousData,
  });

  return (
    <Card
      title="Destek Talepleri"
      extra={
        <Space>
          <Select
            placeholder="Durum filtrele"
            allowClear
            style={{ width: 180 }}
            value={status}
            onChange={(v) => {
              setPage(0);
              setStatus(v);
            }}
            options={["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"].map((s) => ({
              value: s,
              label: s,
            }))}
          />
        </Space>
      }
    >
      <Table<TicketResponse>
        rowKey="id"
        loading={isFetching}
        dataSource={data?.content}
        onRow={(record) => ({
          onClick: () => navigate(`/tickets/${record.id}`),
          style: { cursor: "pointer" },
        })}
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
          { title: "Kategori", dataIndex: "category" },
          {
            title: "Oncelik",
            dataIndex: "priority",
            render: (p: string) => <Tag color={PRIORITY_COLOR[p] ?? "default"}>{p}</Tag>,
          },
          {
            title: "Durum",
            dataIndex: "status",
            render: (s: string) => <Tag color={STATUS_COLOR[s] ?? "default"}>{s}</Tag>,
          },
          {
            title: "Olusturulma",
            dataIndex: "createdAt",
            render: (d: string) => dayjs(d).format("DD.MM.YYYY HH:mm"),
          },
          {
            title: "Atanan",
            dataIndex: "assignedTo",
            render: (v: string | null) => <ShortId value={v} />,
          },
          {
            title: "",
            key: "actions",
            render: (_, record) => (
              <Button
                size="small"
                icon={<RightOutlined />}
                onClick={(e) => {
                  e.stopPropagation();
                  navigate(`/tickets/${record.id}`);
                }}
              >
                Detay
              </Button>
            ),
          },
        ]}
      />
    </Card>
  );
}
