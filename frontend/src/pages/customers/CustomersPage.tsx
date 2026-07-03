import { useState } from "react";
import {
  App,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
} from "antd";
import { PlusOutlined, EditOutlined } from "@ant-design/icons";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import dayjs, { Dayjs } from "dayjs";
import { api } from "../../lib/axios";
import type { ApiResponse, CustomerResponse, RestPage } from "../../api/types";

interface CustomerFormValues {
  type: string;
  firstName: string;
  lastName: string;
  identityNumber?: string;
  dateOfBirth?: Dayjs | null;
  status?: string;
}

const STATUS_COLOR: Record<string, string> = {
  ACTIVE: "green",
  PENDING: "gold",
  SUSPENDED: "orange",
  CLOSED: "red",
};

/** Musteri arama/yonetim (CSR/ADMIN) — server-side sayfalama + q araması + create/update. */
export function CustomersPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [q, setQ] = useState("");
  const [editing, setEditing] = useState<CustomerResponse | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm<CustomerFormValues>();

  const { data, isFetching } = useQuery({
    queryKey: ["customers", page, pageSize, q],
    queryFn: async () => {
      const res = await api.get<ApiResponse<RestPage<CustomerResponse>>>("/api/customers", {
        params: { page, size: pageSize, ...(q ? { q } : {}) },
      });
      return res.data.data!;
    },
    placeholderData: keepPreviousData,
  });

  const save = useMutation({
    mutationFn: async (values: CustomerFormValues) => {
      const body = {
        ...values,
        dateOfBirth: values.dateOfBirth ? values.dateOfBirth.format("YYYY-MM-DD") : null,
      };
      if (editing) {
        return api.put<ApiResponse<CustomerResponse>>(`/api/customers/${editing.id}`, body);
      }
      return api.post<ApiResponse<CustomerResponse>>("/api/customers", body);
    },
    onSuccess: (res) => {
      message.success(res.data.message ?? "Kaydedildi");
      setModalOpen(false);
      void queryClient.invalidateQueries({ queryKey: ["customers"] });
    },
    onError: () => message.error("Kaydetme basarisiz"),
  });

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record: CustomerResponse) => {
    setEditing(record);
    form.setFieldsValue({
      type: record.type,
      firstName: record.firstName,
      lastName: record.lastName,
      identityNumber: record.identityNumber ?? undefined,
      dateOfBirth: record.dateOfBirth ? dayjs(record.dateOfBirth) : null,
      status: record.status,
    });
    setModalOpen(true);
  };

  return (
    <Card
      title="Musteriler"
      extra={
        <Space>
          <Input.Search
            placeholder="ad / soyad / TCKN ara"
            allowClear
            onSearch={(value) => {
              setPage(0);
              setQ(value);
            }}
            style={{ width: 260 }}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Yeni musteri
          </Button>
        </Space>
      }
    >
      <Table<CustomerResponse>
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
          { title: "Ad", dataIndex: "firstName" },
          { title: "Soyad", dataIndex: "lastName" },
          { title: "Tip", dataIndex: "type" },
          { title: "TCKN", dataIndex: "identityNumber", render: (v) => v ?? "-" },
          {
            title: "Durum",
            dataIndex: "status",
            render: (s: string) => <Tag color={STATUS_COLOR[s] ?? "default"}>{s}</Tag>,
          },
          {
            title: "",
            render: (_, record) => (
              <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
                Duzenle
              </Button>
            ),
          },
        ]}
      />

      <Modal
        title={editing ? "Musteri duzenle" : "Yeni musteri"}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={save.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(values) => save.mutate(values)}>
          <Form.Item name="type" label="Tip" rules={[{ required: true }]} initialValue="INDIVIDUAL">
            <Select
              options={[
                { value: "INDIVIDUAL", label: "Bireysel" },
                { value: "CORPORATE", label: "Kurumsal" },
              ]}
              disabled={!!editing}
            />
          </Form.Item>
          <Form.Item name="firstName" label="Ad" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="lastName" label="Soyad" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="identityNumber" label="TCKN">
            <Input maxLength={20} />
          </Form.Item>
          <Form.Item name="dateOfBirth" label="Dogum tarihi">
            <DatePicker style={{ width: "100%" }} />
          </Form.Item>
          {editing && (
            <Form.Item name="status" label="Durum">
              <Select
                options={["ACTIVE", "SUSPENDED", "CLOSED"].map((s) => ({ value: s, label: s }))}
              />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </Card>
  );
}
