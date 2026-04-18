# BookStore App — Distributed Microservices Application

> Developed by **Deepak Srivastav**

---

## About This Project

An e-commerce bookstore application where users can browse books, add them to cart and place orders.

Built with **Java 17**, **Spring Boot 3**, **Spring Cloud**, and **React 18**.

Uses Spring Cloud Microservices architecture with service discovery, API gateway, JWT authentication, and distributed tracing.

---

## Architecture

```
Browser → React Frontend → API Gateway → Microservices → MySQL
                                       ↕
                                    Consul (Service Discovery)
```

All microservices register with **Consul** for service discovery.
The **API Gateway** (Spring Cloud Gateway MVC) routes requests to the appropriate service using load-balanced `lb://` URIs.

For full architecture and deployment details see [DEPLOYMENT.md](DEPLOYMENT.md).

---

## Running Locally

### Prerequisites
- Java 17
- Maven 3.8+
- Node 18+

### Start Order (each in a separate terminal)

```bash
# 1. Eureka Registry
mvn spring-boot:run -f bookstore-eureka-discovery-service/pom.xml

# 2. Account Service
mvn spring-boot:run -f bookstore-account-service/pom.xml

# 3. Catalog Service
mvn spring-boot:run -f bookstore-catalog-service/pom.xml

# 4. Order Service
mvn spring-boot:run -f bookstore-order-service/pom.xml

# 5. Billing Service
mvn spring-boot:run -f bookstore-billing-service/pom.xml

# 6. Payment Service
mvn spring-boot:run -f bookstore-payment-service/pom.xml

# 7. API Gateway (start last)
mvn spring-boot:run -f bookstore-api-gateway-service/pom.xml

# 8. Frontend
npm install --prefix bookstore-frontend-react-app
npm start --prefix bookstore-frontend-react-app
```

### Service Ports

| Service | Port |
|---|---|
| API Gateway | 8765 |
| Eureka Discovery | 8761 |
| Account Service | 4001 |
| Billing Service | 5001 |
| Catalog Service | 6001 |
| Order Service | 7001 |
| Payment Service | 8001 |
| Frontend | 3000 |

---

## Running with Docker

### Prerequisites
- Docker 24+
- Docker Compose plugin

```bash
# Build all JARs
mvn clean install -DskipTests

# Start all containers
docker compose up --build -d

# Check status
docker compose ps

# View logs
docker compose logs -f bookstore-account-service
```

### Docker Service Ports

| Service | Port |
|---|---|
| Frontend | 3000 |
| API Gateway | 8765 |
| Consul UI | 8500 |
| Zipkin | 9411 |
| MySQL | 3306 |

---

## Default Credentials

### OAuth2 Client
```
clientId     : 93ed453e-b7ac-4192-a6d4-c45fae0d99ac
clientSecret : client.devd123
```

### Users

| Username | Password | Role |
|---|---|---|
| `admin.admin` | `admin.devd123` | ADMIN_USER, STANDARD_USER |
| `deepak.srivastav` | `cores.devd123` | STANDARD_USER |

### Get Access Token

```bash
curl -s -X POST http://localhost:8765/api/account/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "93ed453e-b7ac-4192-a6d4-c45fae0d99ac:client.devd123" \
  -d "grant_type=password&username=admin.admin&password=admin.devd123"
```

---

## Service Discovery

- **Local profile** → Eureka (http://localhost:8761)
- **Docker/K8s profile** → Consul (http://localhost:8500)

Consul is preferred in Docker/K8s because it supports health checks, multi-datacenter, and dynamic service registration without extra configuration.

---

## Monitoring

| Tool | Port | Purpose |
|---|---|---|
| Zipkin | 9411 | Distributed tracing |
| Prometheus | 9090 | Metrics scraping |
| Grafana | 3030 | Metrics dashboards (admin/admin) |
| InfluxDB | 8086 | Time-series metrics |
| Chronograf | 8888 | TICK stack UI |

---

## Deployment

See [DEPLOYMENT.md](DEPLOYMENT.md) for:
- Docker Compose setup
- AWS EKS Kubernetes deployment
- Networking architecture
- Multi-instance scaling
- Environment variables reference

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.3, Spring Cloud 2023 |
| Auth | Spring Authorization Server 1.x (OAuth2 + JWT) |
| Gateway | Spring Cloud Gateway MVC |
| Discovery | Consul / Eureka |
| Database | MySQL 8 / H2 (local) |
| Migrations | Flyway |
| Frontend | React 18, Redux, React Bootstrap |
| Tracing | Micrometer + Zipkin |
| Containers | Docker, Docker Compose |
| Cloud | AWS EKS (Kubernetes) |
