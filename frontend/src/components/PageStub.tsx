import { Card, Empty } from "antd";

/** Henuz insa edilmemis sayfa yer tutucusu (backend API'leri hazir — FRONTEND.md §13 eslemesi). */
export function PageStub({ title, sprint }: { title: string; sprint: string }) {
  return (
    <Card title={title}>
      <Empty description={sprint} />
    </Card>
  );
}
