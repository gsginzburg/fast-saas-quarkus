# fast-saas-quarkus

A multi-tenant SaaS platform framework built on [Quarkus 3.17.4](https://quarkus.io/) and Java 25.

## Overview

`fast-saas-quarkus` provides the infrastructure layer for building scalable, multi-tenant SaaS applications. Tenants are fully isolated via PostgreSQL schemas; a central dispatch service handles authentication and orchestration, while lightweight cluster applications handle tenant workloads.

```
┌─────────────────────────────────────────────────────────┐
│                    dispatch-service                      │
│  Backoffice API · Cluster registry · User management    │
│  JWT issuance (BACKOFFICE / TENANT_EXCHANGE tokens)      │
└───────────────────────┬─────────────────────────────────┘
                        │ registers clusters, issues tokens
          ┌─────────────▼──────────────┐
          │      cluster-framework     │
          │  Multi-tenancy CDI library │
          │  Per-tenant Flyway schemas │
          │  Shard routing · JWT auth  │
          └─────────────┬──────────────┘
                        │ extended by
          ┌─────────────▼──────────────┐
          │      cluster-sample        │
          │  Example cluster service   │
          │  TestRecord domain object  │
          └────────────────────────────┘
```

## Modules

| Module | Description |
|--------|-------------|
| `shared-core` | DTOs, exceptions, JWT claims model, `Base62` UUID codec, generic `DtoConverter` |
| `dispatch-service` | Backoffice REST API — cluster, tenant, and user management; JWT auth with pluggable external providers |
| `cluster-framework` | Quarkus CDI library — per-tenant schema routing, Flyway migrations, shard management, JWT verification, path-based tenant injection, bulk schema upgrade |
| `cluster-sample` | Reference cluster application built on `cluster-framework` |

## Key Features

- **Schema-per-tenant isolation** — every tenant gets its own PostgreSQL schema; Hibernate ORM's multi-tenancy support routes queries automatically via `TenantContextHolder`
- **Shard routing** — tenants are distributed across multiple database shards; `TenantShardCache` maintains the tenant→shard mapping with a 10-minute scheduled refresh
- **Three-tier JWT flow** — `BACKOFFICE` → `TENANT_EXCHANGE` → `CLUSTER_SESSION` tokens; each tier carries only the claims required for that scope
- **Path-based tenant injection** — URLs of the form `/c/{base62TenantId}/api-path` allow privileged token types to select a tenant via the URL instead of a JWT claim; the Base62 segment decodes to a tenant UUID
- **Bulk schema upgrade** — `POST /api/management/tenants/upgrade-all` upgrades every tenant schema in parallel with a configurable per-shard thread pool
- **Generic `DtoConverter`** — reflection-based bidirectional mapping between DTO records and domain entities; handles `String`↔`UUID` for `*Id` fields and `String`↔`Enum` automatically
- **Jakarta Bean Validation** — request DTOs are annotated and validated at the REST boundary; violations are returned as structured `400` responses

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 25 (GraalVM CE 25.0.2 recommended) |
| Maven | 3.9+ |
| PostgreSQL | 15+ |

```bash
# Install GraalVM CE 25 via SDKMAN
sdk install java 25.0.2-graalce
export JAVA_HOME=$HOME/.sdkman/candidates/java/25.0.2-graalce
```

## Getting Started

### 1. Start PostgreSQL

```bash
docker-compose -f docker-compose.dev.yml up -d
```

### 2. Build all modules

```bash
mvn install -DskipTests
```

### 3. Run the dispatch service

```bash
cd dispatch-service
mvn quarkus:dev
```

Dispatch API is available at `http://localhost:8080`.

### 4. Run a cluster application

```bash
cd cluster-sample
mvn quarkus:dev
```

Cluster API is available at `http://localhost:8081`.

## Configuration

### Dispatch service (`dispatch-service/src/main/resources/application.properties`)

```properties
dispatch.jwt.secret=change-this-in-production
dispatch.jwt.issuer=dispatch
```

### Cluster application (`cluster-sample/src/main/resources/application.properties`)

```properties
# Cluster identity
cluster.id=cluster-1
cluster.name=Sample Cluster
cluster.dispatch-url=http://localhost:8080
cluster.jwt.secret=change-this-in-production

# Database shards
cluster.shards.shard-1.jdbc-url=jdbc:postgresql://localhost:5432/cluster
cluster.shards.shard-1.username=cluster
cluster.shards.shard-1.password=cluster

# Path-based tenant injection (optional)
# cluster.path-tenant-injection.enabled=true
# cluster.path-tenant-injection.allowed-token-types=BACKOFFICE,SERVICE_ACCOUNT

# Schema upgrade parallelism per shard (default: 4)
# cluster.schema-upgrade.parallelism-per-shard=4
```

## REST API

### Dispatch service

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `POST` | `/api/auth/login` | — | Authenticate and receive JWT tokens |
| `POST` | `/api/auth/refresh` | — | Refresh access token |
| `GET` | `/api/backoffice/clusters` | BACKOFFICE | List clusters (paginated) |
| `POST` | `/api/backoffice/clusters` | BACKOFFICE | Create cluster |
| `GET` | `/api/backoffice/tenants` | BACKOFFICE | List tenants (paginated) |
| `POST` | `/api/backoffice/tenants` | BACKOFFICE | Create tenant |
| `POST` | `/api/backoffice/tenants/{id}/users` | BACKOFFICE | Assign user to tenant |
| `GET` | `/api/backoffice/users` | BACKOFFICE | List users (paginated) |
| `POST` | `/api/backoffice/users` | BACKOFFICE | Create user |

### Cluster management (provided by `cluster-framework`)

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `POST` | `/api/management/tenants` | BACKOFFICE | Provision tenant schema |
| `DELETE` | `/api/management/tenants/{id}` | BACKOFFICE | Archive tenant schema |
| `POST` | `/api/management/tenants/{id}/move` | BACKOFFICE | Move tenant to another shard |
| `POST` | `/api/management/tenants/{id}/upgrade` | BACKOFFICE | Upgrade single tenant schema |
| `POST` | `/api/management/tenants/upgrade-all` | BACKOFFICE | Upgrade all tenant schemas |
| `POST` | `/api/auth/exchange` | BACKOFFICE | Exchange token for cluster session |

## Running Tests

Integration tests require a local PostgreSQL instance (see `docker-compose.dev.yml`):

```bash
mvn verify
```

## License

Copyright 2026 Gary Ginzburg

Licensed under the [Apache License, Version 2.0](LICENSE).
