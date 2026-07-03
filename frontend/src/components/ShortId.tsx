import { Tooltip, Typography } from "antd";

/** UUID'yi kisaltip (ilk 8 karakter + …) gosterir; tam deger Tooltip'te ve kopyalanabilir. */
export function ShortId({ value }: { value?: string | null }) {
  if (!value) {
    return <>-</>;
  }
  return (
    <Tooltip title={value}>
      <Typography.Text copyable={{ text: value }} style={{ whiteSpace: "nowrap" }}>
        {value.slice(0, 8)}…
      </Typography.Text>
    </Tooltip>
  );
}
