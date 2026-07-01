# CQRS (Command Query Responsibility Segregation) — Platform Deseni

Bu doküman, platform genelinde uygulanan **mediator tabanlı CQRS** yapısını anlatır.
Yazma (Command) ve okuma (Query) sorumlulukları ayrı model + handler'lara bölünür; controller'lar
handler'lara doğrudan değil, ortak bir **Mediator** üzerinden erişir (MediatR benzeri yaklaşım).

> Referans yapı: hocanın [turkcell-gygy-5/spring-cqrs](https://github.com/halitkalayci/turkcell-gygy-5/tree/master/spring-cqrs)
> projesindeki mediator + pipeline + feature-based (vertical slice) tasarımı esas alınmıştır.

---

## 1. Neden CQRS ve Neden Mediator?

- **Sorumlulukların ayrılması:** Yazma ve okuma yollarının iş mantığı izole edilir.
- **Performans/ölçeklenebilirlik:** Okuma yolları bağımsız optimize edilebilir (örn. Redis `@Cacheable`),
  yazma yolları cache'i geçersiz kılar (`@CacheEvict`).
- **Bakım kolaylığı:** Büyük `*ServiceImpl` sınıfları yerine her operasyona özel küçük handler'lar.
- **Mediator:** Controller yalnızca `Mediator`'a bağımlıdır; capraz kesitler (loglama, ileride
  authorization/validation) pipeline behavior'ları ile tek yerden eklenir.

---

## 2. Mimari Karar: Framework `common-lib`'te, Auto-Configuration ile

CQRS **altyapısı** (Mediator, pipeline, `Command/Query/Handler` arayüzleri) `common-lib`'e konur ve
Spring Boot **auto-configuration** ile tüm servislere dağıtılır — component-scan **değil**.

**Neden auto-config (sektör standardı):** Paylaşılan bir kütüphanenin bean'lerini tüketici servislere
dağıtmanın standart yolu Spring Boot starter/auto-config desenidir. Consumer'ı kütüphanenin iç paket
yapısına bağlamaz, koşullu (`@ConditionalOnMissingBean`) ve override edilebilir. Repo zaten bu deseni
kullanıyor (`ResourceServerSecurityAutoConfiguration`, `CommonOpenApiAutoConfiguration`, ...).

**Hibrit sonuç:**
- **Framework** → `common-lib` (`com.turkcell.commonlib.cqrs.*`), `CqrsAutoConfiguration` ile bean olur.
- **Feature'lar** (command/query/handler/mapper/rule) → her servisin **kendi** base paketinde,
  servisin normal component-scan'i ile bulunur.

Bu sayede henüz boş olan servisler (identity/usage/ticket) ileride kod aldığında ekstra kurulum
yapmadan `implements Command/Query` + `mediator.send(...)` ile CQRS'i kullanır.

---

## 3. Framework (`common-lib` / `com.turkcell.commonlib.cqrs`)

```text
common-lib/.../commonlib/cqrs/
├── Command.java                 # Command<R> belirtec arayuzu (R = donus tipi)
├── Query.java                   # Query<R> belirtec arayuzu
├── CommandHandler.java          # CommandHandler<C extends Command<R>, R> { R handle(C) }
├── QueryHandler.java            # QueryHandler<Q extends Query<R>, R> { R handle(Q) }
├── Mediator.java                # send(Command<R>) / send(Query<R>)
├── SpringMediator.java          # reflection ile handler cozer + pipeline calistirir
├── CqrsAutoConfiguration.java   # Mediator + LoggingBehavior bean'lerini saglar (auto-config)
└── pipeline/
    ├── PipelineBehavior.java        # handle(request, next) + supports(request)
    ├── RequestHandlerDelegate.java  # @FunctionalInterface -> zincir adimi
    ├── LoggingBehavior.java         # @Order(20), her istegi loglar (NotLoggableRequest haric)
    └── NotLoggableRequest.java      # loglanmamasi gereken istekler icin belirtec
```

`CqrsAutoConfiguration` `META-INF/spring/.../AutoConfiguration.imports` dosyasına eklidir.

### `SpringMediator` — proxy-aware handler çözümü
Handler'lar `@Cacheable`/`@Transactional` ile **CGLIB proxy'lenebildiği** için, hocanın referansındaki
naif reflection (`TODO: Refactor` notlu) yerine gerçek tip
`AopProxyUtils.ultimateTargetClass(bean)` ile çözülür; dönen (proxy'li) bean üzerinde cache/transaction
advice'ı korunur. Eşleştirme sonucu handler singleton olduğu için `ConcurrentHashMap`'te cache'lenir.

Bu davranış izole bir testle doğrulanmıştır: `common-lib/src/test/.../cqrs/SpringMediatorTest.java`
(command/query eşleştirme + `@Cacheable` proxy + record-accessor SpEL key + "handler bulunamadı").

---

## 4. Servis İçi Feature Yapısı (vertical slice)

Her servis, kendi base paketi altında `application/features/<entity>/` yapısını kullanır:

```text
application/features/<entity>/
├── command/<action>/
│   ├── <Action>Command.java         # record ... implements Command<Response> (+ @Valid)
│   └── <Action>CommandHandler.java  # @Component implements CommandHandler<Command, Response>
├── query/<action>/
│   ├── <Action>Query.java           # record ... implements Query<Response>
│   └── <Action>QueryHandler.java    # @Component implements QueryHandler<Query, Response>
├── mapper/<Entity>Mapper.java       # entity <-> command/response donusumleri
└── rule/<Entity>BusinessRules.java  # is kurallari (opsiyonel)
```

Controller yalnızca `Mediator`'a bağımlıdır ve cevabı platform standardı `ApiResponse<T>` ile sarar:

```java
@PostMapping
@PreAuthorize("hasRole('CATALOG_ADMIN')")
public ApiResponse<TariffResponse> create(@Valid @RequestBody CreateTariffCommand command) {
    return ApiResponse.ok(mediator.send(command), "Tarife olusturuldu");
}
```

> Not: Hoca cevabı doğrudan döner; biz gateway/BFF/Feign kontratı gereği `ApiResponse<T>` zarfını korur,
> command'i ise hoca gibi doğrudan `@RequestBody` olarak bağlarız (ayrı `Create*Request` DTO'su yok).

---

## 5. Uygulanan Servisler

| Servis | Command | Query | Cache |
|---|---|---|---|
| **product-catalog-service** | `CreateTariffCommand` | `GetTariffByCodeQuery`, `ListTariffsQuery` | `@Cacheable` okuma, `@CacheEvict` yazma |
| **order-service** | `PlaceOrderCommand` (Feign doğrulama + saga başlatma, `@Transactional`) | `GetOrderQuery` (`@Transactional(readOnly)`) | — |
| **customer-service** | — (yazma endpoint'i yok) | `GetCustomerByIdQuery` (`@Cacheable`), `GetAllCustomersQuery` | `@Cacheable` okuma |

**Neden diğerleri değil:** subscription/payment (saf saga participant) ve billing/notification (saf Kafka
consumer) zaten event-handler yapısındadır; controller→command/query split'i uygulanmaz. identity/usage/ticket
şimdilik boş iskelettir — kod aldıklarında bu framework'ü kullanacaklardır (§6).

---

## 6. Yeni Bir Servise/Feature'a CQRS Ekleme

1. Servis zaten `common-lib`'e bağımlıysa framework hazırdır (auto-config).
2. `application/features/<entity>/...` altında command/query + handler'ları oluştur.
3. Command/Query için: `record X... implements Command<Resp>` / `Query<Resp>`.
4. Handler için: `@Component class XHandler implements CommandHandler<X, Resp>` (veya `QueryHandler`).
5. Controller'a `Mediator` inject et, `mediator.send(...)` çağır, `ApiResponse` ile sar.
6. Okuma yoğunsa handler'a `@Cacheable`, ilgili yazma handler'ına `@CacheEvict` ekle.

---

## 7. Test Edilmesi

- **Birim/izolasyon:** `SpringMediatorTest` (DB/Kafka/Redis gerektirmez) — `./mvnw -pl common-lib test`.
- **Uçtan uca:** Proje kök dizinindeki
  **[telco_crm_postman_collection.json](./telco_crm_postman_collection.json)** koleksiyonu. Endpoint'ler ve
  JSON gövdeleri değişmediği için mevcut istekler aynen çalışır.
