import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  App,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  List,
  Popconfirm,
  Result,
  Space,
  Spin,
  Tag,
  Typography,
} from "antd";
import { ArrowLeftOutlined, SendOutlined, UserAddOutlined } from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AxiosError } from "axios";
import dayjs from "dayjs";
import { api } from "../../lib/axios";
import { apiErrorMessage } from "../../lib/apiError";
import { ShortId } from "../../components/ShortId";
import type {
  ApiResponse,
  TicketCommentResponse,
  TicketDetailResponse,
  TicketResponse,
  UserProfileResponse,
} from "../../api/types";

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

/** Backend'deki gecis matrisinin aynasi (TicketBusinessRules). CLOSED terminaldir. */
export const TRANSITIONS: Record<string, string[]> = {
  OPEN: ["IN_PROGRESS", "CLOSED"],
  IN_PROGRESS: ["RESOLVED", "CLOSED"],
  RESOLVED: ["IN_PROGRESS", "CLOSED"],
  CLOSED: [],
};

export const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function transitionLabel(current: string, target: string): string {
  if (target === "IN_PROGRESS") {
    return current === "RESOLVED" ? "Yeniden ac" : "Isleme al";
  }
  if (target === "RESOLVED") {
    return "Cozuldu isaretle";
  }
  return "Kapat";
}

/**
 * Giris yapan kullanicinin Keycloak sub UUID'sini identity-service'ten cozer.
 * Profil yoksa (404) once /me/sync ile self-provision edip GET'i bir kez tekrarlar.
 */
async function resolveMyKeycloakId(): Promise<string> {
  try {
    const res = await api.get<ApiResponse<UserProfileResponse>>("/api/identity/profiles/me");
    return res.data.data!.keycloakId;
  } catch (error) {
    if (error instanceof AxiosError && error.response?.status === 404) {
      await api.post<ApiResponse<UserProfileResponse>>("/api/identity/profiles/me/sync");
      const res = await api.get<ApiResponse<UserProfileResponse>>("/api/identity/profiles/me");
      return res.data.data!.keycloakId;
    }
    throw error;
  }
}

/** Talep detayi (CSR/ADMIN): meta + durum gecisleri + atama + yorumlar. */
export function TicketDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [assigneeInput, setAssigneeInput] = useState("");
  const [commentForm] = Form.useForm<{ body: string }>();

  const { data, isLoading, isError } = useQuery({
    queryKey: ["tickets", id],
    queryFn: async () => {
      const res = await api.get<ApiResponse<TicketDetailResponse>>(`/api/tickets/${id}`);
      return res.data.data!;
    },
    enabled: !!id,
  });

  const invalidateTicket = () =>
    void queryClient.invalidateQueries({ queryKey: ["tickets"] });

  const transition = useMutation({
    mutationFn: (targetStatus: string) =>
      api.patch<ApiResponse<TicketResponse>>(`/api/tickets/${id}/status`, { targetStatus }),
    onSuccess: (res) => {
      message.success(res.data.message ?? "Durum guncellendi");
      invalidateTicket();
    },
    onError: (error) => message.error(apiErrorMessage(error, "Durum gecisi basarisiz")),
  });

  // variables === null -> "Bana ata" (keycloakId identity-service'ten cozulur)
  const assign = useMutation({
    mutationFn: async (assigneeId: string | null) => {
      const target = assigneeId ?? (await resolveMyKeycloakId());
      return api.patch<ApiResponse<TicketResponse>>(`/api/tickets/${id}/assignee`, {
        assigneeId: target,
      });
    },
    onSuccess: (res) => {
      message.success(res.data.message ?? "Talep atandi");
      setAssigneeInput("");
      invalidateTicket();
    },
    onError: (error) => message.error(apiErrorMessage(error, "Atama basarisiz")),
  });

  const addComment = useMutation({
    mutationFn: (body: string) =>
      api.post<ApiResponse<TicketCommentResponse>>(`/api/tickets/${id}/comments`, { body }),
    onSuccess: (res) => {
      message.success(res.data.message ?? "Yorum eklendi");
      commentForm.resetFields();
      void queryClient.invalidateQueries({ queryKey: ["tickets", id] });
    },
    onError: (error) => message.error(apiErrorMessage(error, "Yorum eklenemedi")),
  });

  const handleAssignOther = () => {
    if (assign.isPending) {
      return; // Enter ile cift gonderimi engelle
    }
    const value = assigneeInput.trim();
    if (!UUID_RE.test(value)) {
      message.error("Gecersiz UUID formati");
      return;
    }
    assign.mutate(value);
  };

  if (isLoading) {
    return <Spin style={{ display: "block", margin: "120px auto" }} size="large" />;
  }
  if (isError || !data) {
    return (
      <Result
        status="warning"
        title="Talep yuklenemedi"
        extra={
          <Button type="primary" onClick={() => navigate("/tickets")}>
            Taleplere don
          </Button>
        }
      />
    );
  }

  const { ticket, comments } = data;
  const targets = TRANSITIONS[ticket.status] ?? [];
  const fmt = (d: string | null) => (d ? dayjs(d).format("DD.MM.YYYY HH:mm") : "-");

  return (
    <Space direction="vertical" size="middle" style={{ width: "100%" }}>
      <Button type="link" icon={<ArrowLeftOutlined />} onClick={() => navigate("/tickets")} style={{ paddingLeft: 0 }}>
        Destek Taleplerine don
      </Button>

      <Card
        title={
          <Space>
            Talep
            <ShortId value={ticket.id} />
            <Tag color={STATUS_COLOR[ticket.status] ?? "default"}>{ticket.status}</Tag>
          </Space>
        }
        extra={
          <Space wrap>
            {targets.map((target) =>
              target === "CLOSED" ? (
                <Popconfirm
                  key={target}
                  title="Talep kapatilsin mi?"
                  okText="Kapat"
                  cancelText="Vazgec"
                  onConfirm={() => transition.mutate(target)}
                >
                  <Button danger loading={transition.isPending && transition.variables === target}>
                    Kapat
                  </Button>
                </Popconfirm>
              ) : (
                <Button
                  key={target}
                  type="primary"
                  ghost
                  loading={transition.isPending && transition.variables === target}
                  onClick={() => transition.mutate(target)}
                >
                  {transitionLabel(ticket.status, target)}
                </Button>
              ),
            )}
            {targets.length === 0 && <Typography.Text type="secondary">Talep kapali</Typography.Text>}
          </Space>
        }
      >
        <Descriptions column={{ xs: 1, sm: 2, lg: 3 }} size="small" bordered>
          <Descriptions.Item label="Musteri">
            <ShortId value={ticket.customerId} />
          </Descriptions.Item>
          <Descriptions.Item label="Kategori">{ticket.category}</Descriptions.Item>
          <Descriptions.Item label="Oncelik">
            <Tag color={PRIORITY_COLOR[ticket.priority] ?? "default"}>{ticket.priority}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="Atanan">
            <ShortId value={ticket.assignedTo} />
          </Descriptions.Item>
          <Descriptions.Item label="SLA">{fmt(ticket.slaDueAt)}</Descriptions.Item>
          <Descriptions.Item label="Olusturulma">{fmt(ticket.createdAt)}</Descriptions.Item>
          <Descriptions.Item label="Guncelleme">{fmt(ticket.updatedAt)}</Descriptions.Item>
          <Descriptions.Item label="Cozulme">{fmt(ticket.resolvedAt)}</Descriptions.Item>
        </Descriptions>

        <Space wrap style={{ marginTop: 16 }}>
          <Button
            icon={<UserAddOutlined />}
            loading={assign.isPending && assign.variables === null}
            disabled={assign.isPending && assign.variables !== null}
            onClick={() => assign.mutate(null)}
          >
            Bana ata
          </Button>
          <Space.Compact style={{ width: 420 }}>
            <Input
              placeholder="Atanacak kullanicinin Keycloak UUID'si"
              value={assigneeInput}
              disabled={assign.isPending}
              onChange={(e) => setAssigneeInput(e.target.value)}
              onPressEnter={handleAssignOther}
            />
            <Button
              loading={assign.isPending && assign.variables !== null}
              onClick={handleAssignOther}
            >
              Ata
            </Button>
          </Space.Compact>
        </Space>
      </Card>

      <Card title={`Yorumlar (${comments.length})`}>
        <List
          dataSource={comments}
          locale={{ emptyText: "Henuz yorum yok" }}
          renderItem={(comment) => (
            <List.Item key={comment.id}>
              <List.Item.Meta
                title={
                  <Space>
                    <ShortId value={comment.authorId} />
                    <Typography.Text type="secondary" style={{ fontWeight: "normal" }}>
                      {dayjs(comment.createdAt).format("DD.MM.YYYY HH:mm")}
                    </Typography.Text>
                  </Space>
                }
                description={
                  <Typography.Paragraph style={{ whiteSpace: "pre-wrap", marginBottom: 0 }}>
                    {comment.body}
                  </Typography.Paragraph>
                }
              />
            </List.Item>
          )}
        />
        <Form
          form={commentForm}
          layout="vertical"
          style={{ marginTop: 8 }}
          onFinish={({ body }) => addComment.mutate(body.trim())}
        >
          <Form.Item
            name="body"
            rules={[
              { required: true, whitespace: true, message: "Yorum bos olamaz" },
              { max: 2000, message: "En fazla 2000 karakter" },
            ]}
          >
            <Input.TextArea rows={3} maxLength={2000} showCount placeholder="Yorum yaz..." />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button
              type="primary"
              icon={<SendOutlined />}
              htmlType="submit"
              loading={addComment.isPending}
            >
              Yorum ekle
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </Space>
  );
}
