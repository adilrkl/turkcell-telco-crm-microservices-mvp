import {
  App,
  Button,
  Descriptions,
  Drawer,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../../lib/axios";
import { apiErrorMessage } from "../../lib/apiError";
import { useAuth } from "../../auth/useAuth";
import { ShortId } from "../../components/ShortId";
import type { ApiResponse, CustomerResponse, DocumentResponse } from "../../api/types";

const STATUS_COLOR: Record<string, string> = {
  ACTIVE: "green",
  PENDING: "gold",
  REJECTED: "red",
  SUSPENDED: "orange",
  CLOSED: "red",
};

const DOC_TYPES = ["ID_CARD", "PASSPORT", "DRIVER_LICENSE", "OTHER"];

interface UploadValues {
  type: string;
  fileName: string;
}

interface Props {
  customer: CustomerResponse | null;
  onClose: () => void;
}

/**
 * KYC mini akisi (G3): musterinin belge listesi + mock belge yukleme (CSR/ADMIN) +
 * KYC onay/red (yalniz ADMIN). Onay sarti backend'de PENDING + >=1 belge; UI ayni sarti
 * yansitir (gercek yetki @PreAuthorize'da). Onay/red sonrasi musteri listesi tazelenir.
 */
export function CustomerKycDrawer({ customer, onClose }: Props) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { hasRole } = useAuth();
  const isAdmin = hasRole("ADMIN");
  const [uploadForm] = Form.useForm<UploadValues>();
  const id = customer?.id;

  const { data: docs, isFetching } = useQuery({
    queryKey: ["customer-documents", id],
    queryFn: async () => {
      const res = await api.get<ApiResponse<DocumentResponse[]>>(`/api/customers/${id}/documents`);
      return res.data.data ?? [];
    },
    enabled: !!id,
  });

  const upload = useMutation({
    mutationFn: (values: UploadValues) =>
      api.post<ApiResponse<DocumentResponse>>(`/api/customers/${id}/documents`, values),
    onSuccess: (res) => {
      message.success(res.data.message ?? "Belge yuklendi");
      uploadForm.resetFields();
      void queryClient.invalidateQueries({ queryKey: ["customer-documents", id] });
    },
    onError: (error) => message.error(apiErrorMessage(error, "Belge yuklenemedi")),
  });

  const decide = useMutation({
    mutationFn: ({ action }: { action: "approve" | "reject" }) =>
      api.post<ApiResponse<CustomerResponse>>(`/api/customers/${id}/kyc/${action}`),
    onSuccess: (res) => {
      message.success(res.data.message ?? "Islem tamamlandi");
      void queryClient.invalidateQueries({ queryKey: ["customers"] });
      onClose();
    },
    onError: (error) => message.error(apiErrorMessage(error, "KYC islemi basarisiz")),
  });

  const docCount = docs?.length ?? 0;
  const isPending = customer?.status === "PENDING";
  const canApprove = isAdmin && isPending && docCount > 0;

  return (
    <Drawer title="KYC / Belgeler" width={560} open={!!customer} onClose={onClose} destroyOnHidden>
      {customer && (
        <Space direction="vertical" size="large" style={{ width: "100%" }}>
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="Musteri">
              {customer.firstName} {customer.lastName}
            </Descriptions.Item>
            <Descriptions.Item label="Kimlik">{customer.identityNumber ?? "-"}</Descriptions.Item>
            <Descriptions.Item label="Durum">
              <Tag color={STATUS_COLOR[customer.status] ?? "default"}>{customer.status}</Tag>
            </Descriptions.Item>
          </Descriptions>

          <div>
            <Typography.Text strong>Belgeler ({docCount})</Typography.Text>
            <Table<DocumentResponse>
              rowKey="id"
              size="small"
              style={{ marginTop: 8 }}
              pagination={false}
              loading={isFetching}
              dataSource={docs}
              locale={{ emptyText: "Belge yok" }}
              columns={[
                { title: "Tip", dataIndex: "type" },
                {
                  title: "Dosya",
                  dataIndex: "fileRef",
                  render: (v: string | null) => <ShortId value={v} />,
                },
                {
                  title: "Dogrulama",
                  dataIndex: "verifiedAt",
                  render: (v: string | null) =>
                    v ? <Tag color="green">Dogrulandi</Tag> : <Tag>Beklemede</Tag>,
                },
              ]}
            />
          </div>

          <Form form={uploadForm} layout="inline" onFinish={(values) => upload.mutate(values)}>
            <Form.Item name="type" rules={[{ required: true, message: "Tip" }]} initialValue="ID_CARD">
              <Select
                style={{ width: 160 }}
                options={DOC_TYPES.map((t) => ({ value: t, label: t }))}
              />
            </Form.Item>
            <Form.Item name="fileName" rules={[{ required: true, message: "Dosya adi" }]}>
              <Input placeholder="ornek: kimlik.pdf" />
            </Form.Item>
            <Form.Item>
              <Button htmlType="submit" loading={upload.isPending}>
                Belge ekle
              </Button>
            </Form.Item>
          </Form>

          {isAdmin ? (
            <Space direction="vertical" style={{ width: "100%" }}>
              <Space>
                <Popconfirm
                  title="KYC onaylansin mi? Musteri ACTIVE olur."
                  okText="Onayla"
                  cancelText="Vazgec"
                  disabled={!canApprove}
                  onConfirm={() => decide.mutate({ action: "approve" })}
                >
                  <Button
                    type="primary"
                    disabled={!canApprove}
                    loading={decide.isPending && decide.variables?.action === "approve"}
                  >
                    KYC onayla
                  </Button>
                </Popconfirm>
                <Popconfirm
                  title="KYC reddedilsin mi? Musteri REJECTED olur."
                  okText="Reddet"
                  okButtonProps={{ danger: true }}
                  cancelText="Vazgec"
                  disabled={!isPending}
                  onConfirm={() => decide.mutate({ action: "reject" })}
                >
                  <Button
                    danger
                    disabled={!isPending}
                    loading={decide.isPending && decide.variables?.action === "reject"}
                  >
                    KYC reddet
                  </Button>
                </Popconfirm>
              </Space>
              {isPending && docCount === 0 && (
                <Typography.Text type="warning">Onay icin en az bir belge gerekir.</Typography.Text>
              )}
              {!isPending && (
                <Typography.Text type="secondary">
                  KYC karari yalniz PENDING musteride verilir.
                </Typography.Text>
              )}
            </Space>
          ) : (
            <Typography.Text type="secondary">
              KYC onay/red yalniz ADMIN rolunde gorunur.
            </Typography.Text>
          )}
        </Space>
      )}
    </Drawer>
  );
}
