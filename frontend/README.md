# Frontend Mimarisi ve Teknoloji Kararı - Telco CRM Platform

Bu doküman, Telco CRM mikroservis platformuna eklenecek **frontend (FE)** katmanının
teknoloji seçimini, gerekçelerini, backend mimarisiyle entegrasyon noktalarını ve
adım adım kurulum planını anlatır.

> **Not:** Bu doküman bir **karar ve plan** dokümanıdır. Henüz kod yazılmamıştır.
> Stack: React 18 + TypeScript (Vite) · Ant Design · openapi-generator · TanStack Query
> Backend: Spring Boot 4.0.6 · Spring Cloud 2025.1.1 · BFF (OAuth2 login + TokenRelay)

---

## 1. Özet (TL;DR)

| Katman | Seçim | Tek cümlelik gerekçe |
|---|---|---|
| **Framework** | **React 18 + TypeScript**, build aracı **Vite** | BFF arkasında saf SPA olarak en temiz oturan, en geniş ekosistem |
| **UI kütüphanesi** | **Ant Design (AntD)** | CRM = yoğun tablo/form/filtre; AntD'nin hazır enterprise bileşenleri en hızlı sonucu verir |
| **API client** | **openapi-generator** (`typescript-axios`) | Backend zaten Springdoc OpenAPI üretiyor; spec'ten tam tipli SDK üretilir, elle endpoint yazılmaz |
| **Server state** | **TanStack Query** | Saga durumu (`PENDING → FULFILLED`) polling + cache invalidation için ideal |
| **HTTP / auth taşıma** | **axios** (`withCredentials: true`) + CSRF interceptor | BFF session cookie'si ile same-origin konuşma |
| **Routing / yetki** | **React Router** + Keycloak rol guard'ı | `CUSTOMER / CSR / CATALOG_ADMIN / BILLING_ADMIN / ADMIN` route koruması |

---

## 2. En kritik kısıt: BFF deseni (Backend-for-Frontend)

Bu projenin FE seçimini **belirleyen** en önemli mimari gerçek, `bff-server` (port 9000)
katmanıdır. README §6'da tanımlandığı gibi:

- BFF, **`oauth2Login` (Authorization Code)** akışını yürütür.
- Erişim token'ı (access token) **sunucu tarafı session'da** tutulur.
- Tarayıcıya **sadece session cookie** verilir — **token tarayıcıya hiç inmez**.
- BFF, `TokenRelay` filtresi ile `lb://gateway-server`'a **Bearer token enjekte eder**.
- SPA-dostu **CSRF** koruması ve OIDC logout vardır.

### Bunun FE'ye 3 doğrudan sonucu

1. **Frontend token yönetimi YAPMAZ.** Access/refresh token saklamak, yenilemek, header'a
   eklemek gibi işler **BFF'in sorumluluğudur**. FE bunları tekrar etmemelidir.
2. **Frontend, BFF ile same-origin cookie üzerinden konuşur.** Her istekte
   `withCredentials: true` (cookie gönderimi) gerekir. Token header'ını FE eklemez.
3. **SSR framework (Next.js) burada fazladan/çakışan katmandır.** Next'in kendi
   server + auth katmanı, Spring BFF'in sorumluluğuyla çiftleşir. Bu yüzden tercih
   **saf SPA**'dır (Vite + React). Next.js gerekirse yalnızca statik/SPA export modunda
   anlamlı olur, SSR modunda değil.

### İstek akışı (hedef topoloji)

```
Tarayıcı (SPA, AntD)
   |  fetch /api/...   (Cookie: SESSION=...; withCredentials)
   v
bff-server :9000  ── session'dan token alır, TokenRelay ile Bearer ekler ──┐
   |                                                                        |
   v                                                                        |
gateway-server :8888  ── route + JWT resource-server downstream ───────────┘
   |
   v
iş servisleri (customer/order/catalog/...)  :8082+  (JWT korumalı /api/**)
```

> **Sonuç:** SPA, ya BFF tarafından static olarak serve edilir ya da dev ortamda
> Vite proxy ile BFF'e yönlendirilir. Böylece **CORS derdi olmaz** ve cookie same-origin akar.

---

## 3. Framework: React + TypeScript (Vite)

### Neden React + Vite?

- **BFF arkasında saf SPA** için en olgun, en geniş ekosistem.
- **Vite**: hızlı dev server (HMR), basit proxy konfigürasyonu (BFF'e yönlendirme),
  TypeScript first-class destek.
- CRM ekipleri için işe alım ve örnek bolluğu yüksek.

### Neden Next.js DEĞİL?

- Next.js'in asıl değeri SSR/RSC ve kendi auth/server katmanıdır.
- Bu projede o katmanın işini **Spring BFF zaten yapıyor** → çakışma ve gereksiz karmaşıklık.
- CRM iç-panel (internal admin) uygulaması olduğu için SEO/SSR ihtiyacı da yok.

### Neden Angular değil (alternatif olarak masada kalsa da)?

- Angular Java/enterprise kültürüne yakın ve telco ortamlarında yaygındır; BFF ile
  entegrasyonu da React kadar temiz çalışır.
- React tercih edildi çünkü: AntD + TanStack Query + openapi-generator üçlüsü ile
  CRM kurulumu daha hızlı ve esnek; ekosistem daha geniş.
- **Karar değişebilir:** Ekip ağırlıklı Angular biliyorsa, bu dokümandaki tüm entegrasyon
  ilkeleri (BFF cookie, openapi-generator, rol guard) Angular'da da birebir geçerlidir.

---

## 4. UI kütüphanesi: Ant Design

### Neden Ant Design?

CRM'in doğası tablo/form ağırlıklıdır: müşteri listeleri, sipariş tabloları, tarife
kataloğu, fatura kayıtları, destek talepleri, rol-bazlı görünümler. AntD bu ihtiyaçlara
**hazır ve zengin** bileşenler sunar:

| İhtiyaç | AntD bileşeni |
|---|---|
| Veri tablosu (sıralama/filtre/sayfalama) | `Table` (server-side pagination destekli) |
| Form + validasyon | `Form`, `Form.Item`, kural tabanlı validation |
| Filtre/arama panelleri | `Input`, `Select`, `DatePicker`, `Cascader` |
| Layout / menü / yetki bazlı navigasyon | `Layout`, `Menu`, `Breadcrumb` |
| Bildirim / işlem geri bildirimi | `message`, `notification`, `Modal.confirm` |
| Durum gösterimi (saga state vb.) | `Tag`, `Badge`, `Steps`, `Timeline` |

### Pratik notlar

- **Tablo sayfalama**, backend `RestPage<T>` (common-lib) yapısına uygun şekilde
  server-side kurulmalı (AntD `Table` + `pagination` + TanStack Query).
- **Tema**: AntD `ConfigProvider` ile tek noktadan tema/locale (tr-TR) ayarı.
- **Saga durumları** `Steps`/`Timeline` ile görselleştirilebilir
  (ReserveMsisdn → ChargePayment → ActivateSubscription → Confirmed/Cancelled).

> **Alternatif:** shadcn/ui + TanStack Table (Vite+React ile çalışır, Tailwind tabanlı,
> daha özelleştirilebilir ama tablo/form'u daha çok kendiniz kurarsınız). Bu projede
> hız önceliği nedeniyle **AntD** seçildi.

---

## 5. API client: openapi-generator

### Neden elle axios değil, OpenAPI'den üretim?

Backend zaten **Springdoc OpenAPI** üretiyor (bkz. kök [README](../README.md) — OpenAPI bölümü): her REST servis
`/v3/api-docs` (JSON spec) ve `/swagger-ui.html` açıyor. Bu, FE'de **spec'ten tam tipli
client SDK üretmek** için hazır zemindir.

Avantajları:

- Backend DTO/endpoint değişince FE'de **derleme zamanında tip hatası** alınır
  (runtime'da sürpriz yok).
- Endpoint yolları, query/path parametreleri, request/response tipleri **elle yazılmaz**.
- API sözleşmesi tek kaynaktan (backend OpenAPI spec) türetilir.

### Generator seçimi

| Generator | Çıktı | Bu projeye uygunluk |
|---|---|---|
| **`typescript-axios`** (önerilen) | axios tabanlı tam tipli SDK + model tipleri | AntD + axios interceptor ile cookie/CSRF yönetimi kolay |
| `typescript-fetch` | fetch tabanlı SDK | Daha yalın, ama interceptor yönetimi elle |
| `typescript` (diğer varyantlar) | çeşitli | Genelde gerek yok |

**Karar: `typescript-axios`.** Sebep: BFF cookie + CSRF için axios interceptor en pratik
çözüm, AntD ile uyumlu.

### Servis-bazlı spec sorunu (önemli)

**Gateway/BFF üzerinde merkezi OpenAPI aggregation
şu an YOK**; her servis kendi spec'ini üretir. Spec açık olan servisler (8/10 —
yalnızca payment ve notification REST'sizdir; spec'ler gateway'den DEĞİL doğrudan servis
portundan çekilir, `openapitools.json` bu portları hedeflemeli):

| Servis | OpenAPI JSON |
|---|---|
| identity-service | `http://localhost:8081/v3/api-docs` |
| customer-service | `http://localhost:8082/v3/api-docs` |
| product-catalog-service | `http://localhost:8083/v3/api-docs` |
| order-service | `http://localhost:8084/v3/api-docs` |
| subscription-service | `http://localhost:8085/v3/api-docs` |
| usage-service | `http://localhost:8086/v3/api-docs` |
| billing-service | `http://localhost:8087/v3/api-docs` |
| ticket-service | `http://localhost:8090/v3/api-docs` |

İki yaklaşım:

1. **Servis başına ayrı generate (önerilen başlangıç).** Her servis için ayrı bir
   client paketi/dizini üretilir (`src/api/customer`, `src/api/order`,
   `src/api/catalog`). Basit, hemen çalışır. Yeni servise OpenAPI eklendikçe genişler.
2. **Gateway'de merkezi aggregation (sonraki adım).** Gateway'e tüm servis spec'lerini
   birleştiren bir aggregation eklenip **tek spec'ten** generate edilir. Daha temiz, ama
   backend işi gerektirir.

### Üretim otomasyonu

- `@openapitools/openapi-generator-cli` npm paketi ile `package.json`'a script eklenir:
  ```json
  {
    "scripts": {
      "generate:api": "openapi-generator-cli batch openapitools.json"
    }
  }
  ```
- Backend değiştiğinde `npm run generate:api` ile client yeniden üretilir.
- İleride CI pipeline'a bağlanabilir (spec değişti → client diff kontrolü).

> **Not:** openapi-generator-cli, altında Java çalıştırır (jar indirir). CI/dev
> ortamında Java 21 zaten mevcut olduğu için ek yük değildir.

---

## 6. Auth akışı: BFF ile login/logout

FE token görmediği için login/logout akışı **BFF yönlendirmeleri** üzerinden kurulur.

### Login

1. FE'de "Giriş yap" → tarayıcı `GET http://localhost:9000/oauth2/authorization/keycloak`
   adresine yönlenir (BFF endpoint'i).
2. BFF → Keycloak login sayfası (realm `telco-crm`).
3. Başarılı login → BFF callback → session oluşur → cookie set edilir → FE'ye redirect.
4. FE artık `/api/...` çağrılarını cookie ile yapar; BFF Bearer'ı downstream'e taşır.

### Kullanıcı/rol bilgisini alma — ✅ HAZIR

- FE'nin "kim giriş yaptı, hangi roller var" bilgisine ihtiyacı vardır (menü/route guard için).
- **BFF'te `GET /api/me` eklendi**: `{username, email, fullName, roles[]}` döner.
  Roller session'daki **access token**'ın `realm_access.roles` claim'inden okunur
  (ID token'da realm rolleri yoktur — bu yüzden endpoint BFF'tedir, FE tek başına ulaşamaz).

### Logout

- FE "Çıkış" → BFF logout endpoint'i (OIDC logout) → Keycloak session sonlanır →
  cookie temizlenir.

### 401 davranışı — ✅ HAZIR

- Session düşmüş/expired `/api/**` XHR'ına BFF **temiz 401** döner
  (`SecurityConfig` → `HttpStatusEntryPoint`; tarayıcı navigasyonu ise oauth2Login
  redirect'inde kalır). FE axios interceptor'ı 401'i yakalayıp login redirect'i yapar.
- Vite dev origin'i (`http://localhost:5173`) Keycloak `telco-bff` client'ının
  redirect/webOrigin listesine eklendi (proxy arkasında login akışı çalışır).

---

## 7. CSRF yönetimi

BFF SPA-dostu CSRF kullanır (cookie tabanlı CSRF token). Tipik akış:

- BFF, `XSRF-TOKEN` adında bir cookie set eder.
- FE, state değiştiren isteklerde (POST/PUT/PATCH/DELETE) bu cookie değerini
  `X-XSRF-TOKEN` header'ı olarak göndermelidir.
- axios interceptor ile otomatikleştirilir:
  - İstekten önce `XSRF-TOKEN` cookie'sini oku → `X-XSRF-TOKEN` header'ına koy.

> Cookie/header adlarının BFF'teki Spring Security CSRF konfigürasyonuyla **birebir
> eşleşmesi** gerekir. Backend tarafındaki gerçek isim doğrulanmalı.

---

## 8. Server state: TanStack Query

CRM'de veri çoğunlukla "server state"tir (liste çek, detay çek, mutate et, yenile).
Bunun için Redux yerine **TanStack Query** önerilir.

- **Liste/detay**: `useQuery` ile cache'li veri çekme, AntD `Table` ile birleştirme.
- **Mutasyon**: `useMutation` + başarıdan sonra `invalidateQueries` ile tabloyu tazeleme.
- **Saga polling**: Sipariş sonrası `GET /api/orders/{id}` durumunu (`PENDING_PAYMENT →
  FULFILLED / CANCELLED`) `refetchInterval` ile periyodik çekip AntD `Steps`/`Tag` ile
  gösterme. Terminal duruma ulaşınca polling durur.
- openapi-generator çıktısı (axios fonksiyonları) **queryFn olarak** kullanılır;
  istenirse `orval` gibi araçlarla hook üretimi de mümkündür (bu projede generator
  `typescript-axios` seçildi, hook'lar elle ince bir sarmalla yazılır).

---

## 9. Yetkilendirme (rol-bazlı UI)

Backend method security `@PreAuthorize` ile korur (örn. tarife yazma `CATALOG_ADMIN`).
FE bunu **tekrar uygulamaz, yansıtır** (gerçek güvenlik backend'de):

- `/api/me`'den gelen rollere göre:
  - **Menü/navigasyon** filtrelenir (CSR farklı, CUSTOMER farklı görür).
  - **Route guard**: yetkisiz route'a girişte 403 sayfası veya redirect.
  - **Buton/aksiyon görünürlüğü**: örn. "Tarife ekle" sadece `CATALOG_ADMIN`'de görünür.
- **Önemli:** FE guard yalnızca UX içindir. Asıl yetki kontrolü **her zaman backend'de**
  (`@PreAuthorize` + JWT). FE'de gizlenen bir buton, API'yi korumaz.

Roller (README §2'den):

| Rol | Tipik FE görünümü |
|---|---|
| `CUSTOMER` | Kendi profili, abonelikleri, faturaları, sipariş verme |
| `CSR` | Müşteri arama/yönetim, sipariş, talep (ticket) işlemleri |
| `CATALOG_ADMIN` | Tarife/ürün kataloğu yönetimi |
| `BILLING_ADMIN` | Faturalama görünümü/işlemleri |
| `ADMIN` | Tümü |

---

## 10. Önerilen dizin yapısı

```
frontend/
├── package.json
├── vite.config.ts            # /api ve /oauth2 -> BFF :9000 proxy (dev)
├── tsconfig.json
├── openapitools.json         # openapi-generator: servis spec URL'leri + generator config
├── index.html
└── src/
    ├── main.tsx              # React root + AntD ConfigProvider (tr-TR) + QueryClientProvider
    ├── App.tsx               # Router + Layout
    ├── api/                  # openapi-generator ÇIKTISI (elle düzenlenmez)
    │   ├── customer/         #   customer-service client + modeller
    │   ├── order/            #   order-service client + modeller
    │   └── catalog/          #   product-catalog-service client + modeller
    ├── lib/
    │   ├── axios.ts          # withCredentials + CSRF interceptor + 401 handler
    │   └── queryClient.ts    # TanStack Query yapılandırması
    ├── auth/
    │   ├── useAuth.ts        # /api/me ile kullanıcı + roller
    │   ├── RequireRole.tsx   # rol-bazlı route guard
    │   └── login.ts          # BFF login/logout redirect helper'ları
    ├── layout/
    │   ├── AppLayout.tsx     # AntD Layout + rol-bazlı Menu
    │   └── routes.tsx        # route tanımları
    ├── pages/
    │   ├── customers/        # müşteri listesi/detay (Table + Form)
    │   ├── orders/           # sipariş + saga durum görünümü (Steps/Timeline)
    │   ├── tariffs/          # tarife kataloğu (CATALOG_ADMIN)
    │   ├── billing/          # fatura görünümü (BILLING_ADMIN)
    │   ├── subscriptions/    # abonelikler
    │   └── tickets/          # destek talepleri
    └── components/           # ortak AntD tabanlı bileşenler (DataTable wrapper vb.)
```

---

## 11. Dev ortam: Vite proxy (CORS'suz çalışma)

Dev sırasında SPA `:5173`, BFF `:9000`'de çalışır. CORS'tan kaçınmak ve cookie'nin
same-origin akması için **Vite proxy** kullanılır (kavramsal örnek):

```ts
// vite.config.ts (kavramsal)
server: {
  proxy: {
    "/api":    { target: "http://localhost:9000", changeOrigin: true },
    "/oauth2": { target: "http://localhost:9000", changeOrigin: true },
    "/login":  { target: "http://localhost:9000", changeOrigin: true },
    "/logout": { target: "http://localhost:9000", changeOrigin: true },
  }
}
```

- Böylece FE tüm istekleri kendi origin'ine (`:5173`) atar, Vite bunları BFF'e proxy'ler.
- Cookie ve CSRF same-origin gibi davranır → CORS konfigürasyonuna gerek kalmaz.
- **Prod**: SPA build çıktısı (statik dosyalar) ya BFF tarafından serve edilir ya da
  reverse proxy (nginx) arkasında BFF ile aynı origin'den sunulur.

---

## 12. Adım adım kurulum planı (kod yazılınca izlenecek)

> Bu bölüm uygulama sırasını özetler; bu dokümanda **kod yazılmamıştır**.

1. **Scaffold**: Vite + React + TypeScript projesi (`frontend/`).
2. **Bağımlılıklar**: `antd`, `@tanstack/react-query`, `axios`, `react-router-dom`,
   dev olarak `@openapitools/openapi-generator-cli`.
3. **OpenAPI client**: `openapitools.json` ile servis spec URL'lerini tanımla,
   `npm run generate:api` ile `src/api/*` üret.
4. **axios altyapısı**: `withCredentials`, CSRF interceptor, 401 → login redirect.
5. **Auth**: BFF login/logout helper'ları + `/api/me` ile rol çekme + route guard.
   (Gerekirse BFF'e `/api/me` endpoint'i eklenmesi backend tarafında planlanmalı.)
6. **Layout**: AntD `Layout` + rol-bazlı `Menu` + router.
7. **İlk sayfalar**: customers ve orders (Table + Form + saga durum görünümü) ile
   uçtan uca akışı doğrula (README §"Demo" senaryosuyla eşleştir).
8. **Vite proxy**: BFF'e `/api`, `/oauth2`, `/login`, `/logout` yönlendirmesi.
9. **Doğrulama**: BFF login → token görünmüyor (DevTools) → `/api/...` cookie ile 200 →
   yetkisiz rolde ilgili aksiyon backend'den 403.

---

## 13. Açık kararlar / backend'e bağlı noktalar — GÜNCEL DURUM

| Konu | Durum |
|---|---|
| `/api/me` (kullanıcı + roller) | ✅ BFF'e eklendi — `GET /api/me` → `{username, email, fullName, roles[]}` |
| CSRF cookie/header adları | ✅ Doğrulandı: cookie `XSRF-TOKEN`, header `X-XSRF-TOKEN` (axios'un default'uyla birebir uyumlu) |
| 401 sözleşmesi | ✅ Session düşmüş `/api/**` XHR'ına 401 (302 değil) — interceptor planı geçerli |
| OpenAPI aggregation | Servis-bazlı generate (8 servis, §5 tablosu); merkezi spec ileriki adım |
| Spec açık olmayan servisler | Yalnızca **payment** ve **notification** (REST'siz; FE sayfası da yok) |
| API versiyonlama | Karar: versiyonsuz `/api/**`; gateway **explicit** route kullanır (yeni servis = `gateway-server.yaml`'a route) |
| Prod serve stratejisi | AÇIK: SPA'yı BFF mi serve edecek, yoksa nginx + aynı origin mi |
| CUSTOMER self-servis | AÇIK: kullanıcı↔müşteri bağlantısı yok (Keycloak `sub` ≠ `customerId`); "kendi siparişlerim/faturalarım" için identity profili ile customer kaydı ilişkilendirilmeli. İlk sürüm CSR/ADMIN odaklı ilerlemeli |

### Sayfa ↔ endpoint eşlemesi (backend HAZIR)

| FE sayfası | Endpoint'ler |
|---|---|
| **auth** | `GET /api/me` (BFF) · login `GET /oauth2/authorization/keycloak` · logout `POST /logout` |
| **customers** | `GET /api/customers?q=&page=&size=` (RestPage) · `GET /{id}` · `POST` · `PUT /{id}` (CSR/ADMIN) |
| **orders** | `POST /api/orders` · `GET /api/orders/{id}` (saga poll) · `GET /api/orders?customerId=&status=` (sayfalı, CSR/ADMIN) |
| **tariffs** | `GET /api/catalog/tariffs` (sayfalı) · `GET /{code}` · `POST` (CATALOG_ADMIN) |
| **subscriptions** | `GET /api/subscriptions?customerId=&status=` (sayfalı) · `GET /{id}` (CSR/ADMIN) |
| **billing** | `GET /api/billing/invoices?customerId=&status=` (sayfalı) · `GET /invoices/{id}` (kalemli) · `GET /bill-cycles` (BILLING_ADMIN/CSR/ADMIN) |
| **tickets** | `GET/POST /api/tickets` · `GET /{id}` · `PATCH /{id}/status` · `PATCH /{id}/assignee` · `POST /{id}/comments` (CSR/ADMIN) |
| **usage** (ops.) | `GET /api/usage/records?subscriptionId=` · `GET /api/usage/summary?subscriptionId=&from=&to=` (CSR/ADMIN) |

---

## 14. Neden bu stack — özet gerekçe

- **BFF deseni** token yönetimini backend'e aldığı için FE **saf SPA** olmalı → React + Vite.
- **CRM iş yükü** tablo/form ağırlıklı → **Ant Design** en hızlı sonucu verir.
- Backend **OpenAPI** üretiyor → client'ı **openapi-generator** ile türetmek elle yazmaktan
  hem daha hızlı hem tip-güvenli.
- Veri server-state ağırlıklı + saga polling var → **TanStack Query** doğal seçim.
- Güvenlik backend'de (`@PreAuthorize` + JWT); FE yetkiyi yalnızca **UX için yansıtır**.

> Bu doküman karar ve plandır. Onaylanırsa bir sonraki adım: `frontend/` iskeletinin
> (Vite + React + TS + AntD + openapi-generator config + BFF proxy + CSRF interceptor)
> kurulması.
