/**
 * Backend kontratlarinin hafif el yazimi tipleri (ApiResponse/RestPage + kullanilan DTO'lar).
 * NOT: `npm run generate:api` ile openapi-generator'dan tam tipli client uretilebilir
 * (src/api/generated/ altina, .gitignore'lu); bu dosya scaffold'un calismasi icin yeterlidir.
 */

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  message?: string;
  errorCode?: string;
}

/** common-lib RestPage<T> (Spring Page) serilestirmesi. */
export interface RestPage<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages?: number;
}

export interface MeResponse {
  username: string;
  email: string | null;
  fullName: string | null;
  roles: string[];
}

export interface CustomerResponse {
  id: string;
  type: string;
  firstName: string;
  lastName: string;
  identityNumber: string | null;
  dateOfBirth: string | null;
  status: string;
}

export interface TicketResponse {
  id: string;
  customerId: string;
  category: string;
  priority: string;
  status: string;
  assignedTo: string | null;
  slaDueAt: string | null;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
}

export type Role = "CUSTOMER" | "CSR" | "CATALOG_ADMIN" | "BILLING_ADMIN" | "ADMIN";
