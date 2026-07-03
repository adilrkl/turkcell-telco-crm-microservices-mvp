import { useRef, useState } from "react";
import { Select } from "antd";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { api } from "../lib/axios";
import type { ApiResponse, CustomerResponse, RestPage } from "../api/types";

interface Props {
  value?: string;
  onChange?: (value: string | undefined) => void;
  placeholder?: string;
  style?: React.CSSProperties;
  allowClear?: boolean;
}

/**
 * Server-side arayan musteri Select'i (CSR/ADMIN).
 * onSearch debounce'lanip GET /api/customers?q= parametresine gider; filterOption kapali.
 */
export function CustomerSelect({ value, onChange, placeholder, style, allowClear }: Props) {
  const [search, setSearch] = useState("");
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();

  const { data, isFetching } = useQuery({
    queryKey: ["customers", "select", search],
    queryFn: async () => {
      const res = await api.get<ApiResponse<RestPage<CustomerResponse>>>("/api/customers", {
        params: { page: 0, size: 20, ...(search ? { q: search } : {}) },
      });
      return res.data.data!;
    },
    placeholderData: keepPreviousData,
  });

  const handleSearch = (text: string) => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }
    debounceRef.current = setTimeout(() => setSearch(text), 400);
  };

  return (
    <Select
      showSearch
      filterOption={false}
      onSearch={handleSearch}
      loading={isFetching}
      value={value}
      onChange={(v) => onChange?.(v)}
      placeholder={placeholder ?? "Musteri ara (ad / soyad / TCKN)"}
      style={style}
      allowClear={allowClear}
      options={(data?.content ?? []).map((c) => ({
        value: c.id,
        label: `${c.firstName} ${c.lastName} (${c.id.slice(0, 8)}…)`,
      }))}
    />
  );
}
