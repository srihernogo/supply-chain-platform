# Blockchain Supply Chain & Inventory Platform — Implementation Plan

## Overview

Enterprise-grade **Multi-Tenant Supply Chain & Inventory Platform** with **Private Blockchain Audit Trail** — dibangun dengan Spring Boot 3.x, PostgreSQL, Apache Kafka, Docker, dan CI/CD via GitHub Actions. Target: Senior Java Engineer Portfolio.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         API Gateway                              │
│                     (Spring Cloud Gateway)                       │
└────────────────────────┬────────────────────────────────────────┘
                         │ JWT + TenantId
         ┌───────────────┼──────────────────────┐
         ▼               ▼                      ▼
┌─────────────┐  ┌──────────────┐  ┌────────────────────┐
│  Supplier   │  │  Inventory   │  │ Blockchain Ledger  │
│  Service    │  │   Service    │  │    Service         │
└──────┬──────┘  └──────┬───────┘  └────────┬───────────┘
       │                │                   │
       └────────────────┼───────────────────┘
                        │ Kafka Events
                        ▼
              ┌─────────────────┐
              │  Kafka Broker   │
              │ inventory-events│
              └─────────────────┘
                        │
              ┌─────────▼───────┐
              │  PostgreSQL      │
              │ (per-tenant DB)  │
              └─────────────────┘
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.3.x |
| Security | Spring Security + JWT (JJWT 0.12) |
| Messaging | Apache Kafka 3.7 |
| Database | PostgreSQL 16 |
| Migration | Flyway |
| ORM | Spring Data JPA + Hibernate |
| Observability | Micrometer + Prometheus + Grafana |
| Tracing | OpenTelemetry + Zipkin |
| Testing | JUnit 5, Testcontainers, MockMvc |
| Container | Docker + Docker Compose |
| CI/CD | GitHub Actions |
| Build | Maven |

---

## Project Structure

```
supply-chain-platform/
├── docker-compose.yml
├── docker-compose.monitoring.yml
├── .github/workflows/ci.yml
├── api-gateway/
│   ├── pom.xml
│   └── src/...
├── supplier-service/
│   ├── pom.xml
│   └── src/...
├── inventory-service/
│   ├── pom.xml
│   └── src/...
├── blockchain-ledger-service/
│   ├── pom.xml
│   └── src/...
└── pom.xml (parent)
```

---

## Services Detail

### 1. API Gateway (`api-gateway`)
- Spring Cloud Gateway
- JWT validation filter
- Tenant extraction from JWT claims
- Rate limiting
- Route ke semua services

### 2. Supplier Service (`supplier-service`)
- CRUD Supplier
- Supplier validation status
- REST API

### 3. Inventory Service (`inventory-service`) — Core Service
- Material management
- Warehouse management
- Inventory CRUD
- Transaction: RECEIPT, TRANSFER, RESERVE, ISSUE, ADJUSTMENT
- Kafka producer
- Traceability query
- Idempotency key
- Optimistic locking

### 4. Blockchain Ledger Service (`blockchain-ledger-service`)
- Kafka consumer
- Block creation + SHA-256 hash
- Chain integrity validation
- Chain verification API

---

## Database Schema (per tenant)

### Supplier Service DB
- `tenants` — tenant registry
- `suppliers` — supplier master
- `supplier_contacts`

### Inventory Service DB
- `materials` + `material_categories` + `units_of_measure`
- `warehouses`
- `inventory` (with optimistic locking via `version`)
- `inventory_transactions` (idempotency_key UNIQUE)
- `audit_logs`

### Blockchain Ledger Service DB
- `blockchain_blocks` (hash, previous_hash, payload, timestamp)

---

## Key Technical Implementations

### Multi-Tenant Strategy
- `TenantContext` (ThreadLocal)
- `AbstractRoutingDataSource`
- JWT Claims: `{ "sub": "user1", "tenantId": "toyota", "role": "WAREHOUSE" }`
- Flyway migration per tenant schema

### Blockchain
```java
hash = SHA256(previousHash + transactionId + payload + timestamp)
```
- Genesis block per tenant
- Chain validation API
- Immutable ledger stored in PostgreSQL

### Kafka Flow
```
InventoryService → publish(InventoryEvent) → Kafka
                                              ↓
                              BlockchainLedgerService.consume()
                              → createBlock() → saveBlock()
```

### Production Grade Features
- ✅ Idempotency Key (UUID, stored in DB, checked before processing)
- ✅ Optimistic Locking (`@Version`)
- ✅ DLQ (Dead Letter Queue via Kafka)
- ✅ Distributed Tracing (OpenTelemetry → Zipkin)
- ✅ Metrics (Micrometer → Prometheus → Grafana)
- ✅ Audit Log table
- ✅ Retry with exponential backoff
- ✅ Spring Security + JWT + RBAC

---

## Proposed Changes

### Root
#### [NEW] pom.xml — parent multi-module Maven
#### [NEW] docker-compose.yml — PostgreSQL, Kafka, Zookeeper, Zipkin
#### [NEW] docker-compose.monitoring.yml — Prometheus, Grafana
#### [NEW] .github/workflows/ci.yml — GitHub Actions CI

---

### api-gateway/
#### [NEW] pom.xml
#### [NEW] src/main/java/.../ApiGatewayApplication.java
#### [NEW] src/main/resources/application.yml — routes config
#### [NEW] JwtAuthFilter.java — validate JWT + inject tenant header

---

### supplier-service/
#### [NEW] pom.xml
#### [NEW] SupplierServiceApplication.java
#### [NEW] Supplier.java (entity)
#### [NEW] SupplierRepository.java
#### [NEW] SupplierService.java
#### [NEW] SupplierController.java
#### [NEW] db/migration/V1__init_supplier.sql (Flyway)

---

### inventory-service/
#### [NEW] pom.xml
#### [NEW] InventoryServiceApplication.java
#### [NEW] entities: Material, Warehouse, Inventory, InventoryTransaction, AuditLog
#### [NEW] repositories
#### [NEW] InventoryService.java (core logic)
#### [NEW] InventoryController.java
#### [NEW] TraceabilityController.java
#### [NEW] KafkaProducerConfig.java
#### [NEW] InventoryEventPublisher.java
#### [NEW] TenantContext.java + TenantRoutingDataSource.java
#### [NEW] db/migration/V1__init_inventory.sql

---

### blockchain-ledger-service/
#### [NEW] pom.xml
#### [NEW] BlockchainLedgerServiceApplication.java
#### [NEW] Block.java (entity)
#### [NEW] BlockchainRepository.java
#### [NEW] BlockchainService.java (hash calculation, chain building)
#### [NEW] InventoryEventConsumer.java (Kafka @KafkaListener)
#### [NEW] ChainVerificationController.java
#### [NEW] db/migration/V1__init_blockchain.sql

---

## Verification Plan

### Automated Tests
- `mvn test` di setiap service
- Integration test dengan Testcontainers (PostgreSQL + Kafka)
- `docker-compose up` → smoke test API

### Manual Verification
1. POST `/api/auth/login` → dapat JWT
2. POST `/api/inventory/receipt` → inventory bertambah + block dibuat
3. GET `/api/traceability/material/{id}` → tampil history
4. GET `/api/blockchain/verify` → chain valid
5. Prometheus metrics di `localhost:9090`
6. Grafana dashboard di `localhost:3000`

---

## Open Questions

> [!NOTE]
> Tidak ada open question kritis — spesifikasi dalam PROJECT.md sudah cukup jelas untuk memulai implementasi penuh.

> [!IMPORTANT]
> Project ini akan dibuat sebagai **Maven multi-module** dengan satu repository (monorepo). Setiap service adalah module terpisah. Docker Compose mengatur seluruh infrastruktur.
