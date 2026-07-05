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

/** customer-service DocumentResponse — KYC belgesi (fileRef mock; verifiedAt onayla damgalanir). */
export interface DocumentResponse {
  id: string;
  customerId: string;
  type: string;
  fileRef: string | null;
  verifiedAt: string | null;
}

/** usage-service QuotaItem — tip bazli kota kalemi. type: VOICE (dk) | SMS (adet) | DATA (MB). */
export interface QuotaItem {
  type: string;
  total: number;
  remaining: number;
  usedPct: number;
}

/** usage-service QuotaResponse — bir aboneligin donem kotasi (FE kota karti, G1/FR-19). */
export interface QuotaResponse {
  subscriptionId: string;
  periodStart: string | null;
  periodEnd: string | null;
  items: QuotaItem[];
}

/** billing-service InvoiceResponse — sayfali fatura listesi kalemi. */
export interface InvoiceResponse {
  id: string;
  customerId: string;
  subscriptionId: string | null;
  billCycleId: string | null;
  periodStart: string | null;
  periodEnd: string | null;
  subTotal: number;
  tax: number;
  grandTotal: number;
  status: string;
  dueDate: string | null;
  issuedAt: string | null;
}

/** billing-service InvoiceLineResponse — fatura kalemi (aciklama + adet + birim fiyat). */
export interface InvoiceLineResponse {
  id: string;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

/** GET /api/billing/invoices/{id} — fatura + kalemleri. */
export interface InvoiceDetailResponse {
  invoice: InvoiceResponse;
  lines: InvoiceLineResponse[];
}

/** subscription-service SubscriptionResponse — abonelik + yasam dongusu zaman damgalari. */
export interface SubscriptionResponse {
  id: string;
  orderId: string | null;
  customerId: string;
  msisdn: string | null;
  tariffCode: string;
  status: string;
  activatedAt: string | null;
  suspendedAt: string | null;
  terminatedAt: string | null;
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
