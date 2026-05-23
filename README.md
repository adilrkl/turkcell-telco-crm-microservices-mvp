# Telco CRM Platform

Spring Boot 3.x multi-module Maven mikroservis projesi.

## Servisler

| Servis | Port | Veritabani |
|--------|------|------------|
| eureka-server | 8761 | — |
| gateway-server | 8888 | — |
| identity-service | 8081 | identity_db |
| customer-service | 8082 | customer_db |
| product-catalog-service | 8083 | catalog_db |
| order-service | 8084 | order_db |
| subscription-service | 8085 | subscription_db |
| usage-service | 8086 | usage_db |
| billing-service | 8087 | billing_db |
| payment-service | 8088 | payment_db |
| notification-service | 8089 | notification_db |
| ticket-service | 8090 | ticket_db |

## Baslangic

```bash
# Altyapi ayaga kaldir (Kafka, PostgreSQL'ler, Eureka)
docker-compose up -d

# Tum servisleri derle
mvn clean install -DskipTests

# Tek servis calistir
cd customer-service && mvn spring-boot:run
```

## Mimari

- Database-per-Service pattern
- Transactional Outbox (event-driven servisler)
- Saga pattern (order akisi)
- JWT tabanli kimlik dogrulama
- Flyway ile veritabani migration yonetimi
