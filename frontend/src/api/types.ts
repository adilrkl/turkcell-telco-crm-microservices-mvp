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

export interface TicketCommentResponse {
  id: string;
  authorId: string;
  body: string;
  createdAt: string;
}

/** GET /api/tickets/{id} — talep + kronolojik (createdAt ASC) yorumlar. */
export interface TicketDetailResponse {
  ticket: TicketResponse;
  comments: TicketCommentResponse[];
}

export interface OrderResponse {
  orderId: string;
  customerId: string;
  status: string;
  totalAmount: number;
  currency: string;
  tariffCode: string;
}

export interface TariffResponse {
  id: string;
  code: string;
  name: string;
  type: string;
  monthlyFee: number;
  minutesIncluded: number | null;
  smsIncluded: number | null;
  dataMbIncluded: number | null;
  status: string;
}

/** identity-service UserProfileResponse — "Bana ata" icin keycloakId cozumu. */
export interface UserProfileResponse {
  id: string;
  keycloakId: string;
  username: string;
  email: string | null;
  firstName: string | null;
  lastName: string | null;
  phoneNumber: string | null;
  preferredLanguage: string | null;
  status: string;
}

export type Role = "CUSTOMER" | "CSR" | "CATALOG_ADMIN" | "BILLING_ADMIN" | "ADMIN";
