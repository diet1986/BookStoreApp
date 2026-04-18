# BookStore App — Deployment Guide

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        Browser / Client                      │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP :3000
                           ▼
                  ┌─────────────────┐
                  │  React Frontend  │  (nginx, port 80/3000)
                  └────────┬────────┘
                           │ HTTP :8765
                           ▼
                  ┌─────────────────┐
                  │   API Gateway   │  (Spring Cloud Gateway MVC)
                  └────────┬────────┘
                           │ lb:// (Consul service discovery)
          ┌────────────────┼────────────────────┐
          ▼                ▼                    ▼
  ┌──────────────┐ ┌──────────────┐   ┌──────────────────┐
  │   Account    │ │   Catalog    │   │  Order / Billing │
  │   Service    │ │   Service    │   │  Payment Service │
  │   :4001      │ │   :6001      │   │  :7001/:5001/:8001│
  └──────┬───────┘ └──────┬───────┘   └────────┬─────────┘
         │                │                     │
         └────────────────┴─────────────────────┘
                          │
                   ┌──────▼──────┐
                   │   MySQL 8   │
                   │   :3306     │
                   └─────────────┘

Service Discovery: Consul (:8500)
Tracing:          Zipkin (:9411)
```

---

## How the Dockerized App Works

### 1. Build Phase
Each microservice is built as a fat JAR using Maven:
```bash
mvn clean install -DskipTests -f pom.xml
```

### 2. Docker Images
Each service has a `Dockerfile` that:
- Uses `eclipse-temurin:17-jre-alpine` as base
- Copies the fat JAR into the container
- Uses `dockerize` to wait for MySQL to be ready before starting
- Starts the Spring Boot app with `SPRING_PROFILES_ACTIVE=docker`

```dockerfile
FROM eclipse-temurin:17-jre-alpine
COPY target/service.jar service.jar
COPY dockerize dockerize
ENV DB_HOST=bookstore-mysql-db
ENV DB_PORT=3306
CMD ./dockerize -wait tcp://${DB_HOST}:${DB_PORT} -timeout 15m \
    java -jar /service.jar
```

### 3. Service Discovery (Consul)
In `docker` profile, each service registers with Consul using a unique instance ID:
```yaml
spring.cloud.consul.discovery.instanceId: ${spring.application.name}:${random.value}
```
The gateway uses `lb://service-name` to load balance across all registered instances.

### 4. Token Storage (JDBC)
OAuth2 tokens are stored in MySQL (`oauth2_authorization` table), enabling multiple account-service instances to share token state.

---

## Running Locally (Development)

### Prerequisites
- Java 17
- Maven 3.8+
- Node 18+ (for frontend)

### Start Order
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

# 7. API Gateway (last)
mvn spring-boot:run -f bookstore-api-gateway-service/pom.xml

# 8. Frontend
npm start --prefix bookstore-frontend-react-app
```

### Default Credentials
| Username | Password | Role |
|---|---|---|
| `admin.admin` | `admin.devd123` | ADMIN_USER, STANDARD_USER |
| `devd.cores` | `devd.cores123` | STANDARD_USER |

---

## Running with Docker Compose

### Prerequisites
- Docker 24+
- Docker Compose plugin

### Build JARs
```bash
mvn clean install -DskipTests
```

### Start All Services
```bash
docker compose up --build -d
```

### Check Status
```bash
docker compose ps
docker compose logs -f bookstore-account-service
```

### Stop All Services
```bash
docker compose down
```

### Access Points
| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |
| API Gateway | http://localhost:8765 |
| Consul UI | http://localhost:8500 |
| Zipkin UI | http://localhost:9411 |

---

## Kubernetes Deployment (AWS EKS)

### Architecture on AWS

```
Internet
    │
    ▼
┌─────────────────────────────────────────────────────┐
│  AWS Application Load Balancer (ALB)                 │
│  Ingress Controller (AWS Load Balancer Controller)   │
└──────────────────────────┬──────────────────────────┘
                           │
                    ┌──────▼──────┐
                    │  EKS Cluster │
                    │             │
          ┌─────────┴─────────────┴──────────┐
          │         Kubernetes Nodes          │
          │                                   │
          │  ┌──────────┐  ┌──────────────┐  │
          │  │ Frontend │  │  API Gateway │  │
          │  │ Pod(s)   │  │  Pod(s)      │  │
          │  └──────────┘  └──────────────┘  │
          │                                   │
          │  ┌──────────┐  ┌──────────────┐  │
          │  │ Account  │  │   Catalog    │  │
          │  │ Pod(s)   │  │   Pod(s)     │  │
          │  └──────────┘  └──────────────┘  │
          │                                   │
          │  ┌──────────┐  ┌──────────────┐  │
          │  │  Order   │  │   Billing    │  │
          │  │ Pod(s)   │  │   Pod(s)     │  │
          │  └──────────┘  └──────────────┘  │
          └───────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
   ┌────────────┐  ┌─────────────┐  ┌──────────────┐
   │  AWS RDS   │  │  AWS ECS    │  │  AWS ECR     │
   │  MySQL 8   │  │  Consul     │  │  (Images)    │
   └────────────┘  └─────────────┘  └──────────────┘
```

### Step 1 — Push Images to ECR

```bash
# Authenticate with ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  <account-id>.dkr.ecr.us-east-1.amazonaws.com

# Build and push each service
for service in account catalog order billing payment api-gateway frontend; do
  docker build -t bookstore-${service}-service \
    --build-arg JAR_FILE=bookstore-${service}-service-0.0.1-SNAPSHOT.jar \
    bookstore-${service}-service/
  
  docker tag bookstore-${service}-service \
    <account-id>.dkr.ecr.us-east-1.amazonaws.com/bookstore-${service}-service:latest
  
  docker push \
    <account-id>.dkr.ecr.us-east-1.amazonaws.com/bookstore-${service}-service:latest
done
```

### Step 2 — Create EKS Cluster

```bash
eksctl create cluster \
  --name bookstore-cluster \
  --region us-east-1 \
  --nodegroup-name bookstore-nodes \
  --node-type t3.medium \
  --nodes 3 \
  --nodes-min 2 \
  --nodes-max 5 \
  --managed
```

### Step 3 — Kubernetes Manifests

#### Namespace
```yaml
# k8s/namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: bookstore
```

#### ConfigMap (shared config)
```yaml
# k8s/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: bookstore-config
  namespace: bookstore
data:
  SPRING_PROFILES_ACTIVE: "kubernetes"
  CONSUL_HOST: "consul-service"
  CONSUL_PORT: "8500"
  DB_HOST: "<rds-endpoint>"
  DB_PORT: "3306"
  DB_NAME: "bookstore_db"
  ZIPKIN_HOST: "zipkin-service"
```

#### Secret (sensitive config)
```yaml
# k8s/secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: bookstore-secrets
  namespace: bookstore
type: Opaque
stringData:
  DB_USER: "bookstoreDBA"
  DB_PASSWORD: "PaSSworD"
  STRIPE_SECRET_KEY: "sk_test_..."
```

#### Account Service Deployment
```yaml
# k8s/account-service.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: bookstore-account-service
  namespace: bookstore
spec:
  replicas: 2                          # 2 instances
  selector:
    matchLabels:
      app: bookstore-account-service
  template:
    metadata:
      labels:
        app: bookstore-account-service
    spec:
      containers:
      - name: account-service
        image: <account-id>.dkr.ecr.us-east-1.amazonaws.com/bookstore-account-service:latest
        ports:
        - containerPort: 4001
        envFrom:
        - configMapRef:
            name: bookstore-config
        - secretRef:
            name: bookstore-secrets
        env:
        - name: SERVER_PORT
          value: "4001"
        - name: OAUTH2_ISSUER_URI
          value: "http://bookstore-account-service:4001"
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 4001
          initialDelaySeconds: 30
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 4001
          initialDelaySeconds: 60
          periodSeconds: 30
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: bookstore-account-service
  namespace: bookstore
spec:
  selector:
    app: bookstore-account-service
  ports:
  - port: 4001
    targetPort: 4001
  type: ClusterIP
```

#### API Gateway Deployment
```yaml
# k8s/api-gateway.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: bookstore-api-gateway
  namespace: bookstore
spec:
  replicas: 2
  selector:
    matchLabels:
      app: bookstore-api-gateway
  template:
    metadata:
      labels:
        app: bookstore-api-gateway
    spec:
      containers:
      - name: api-gateway
        image: <account-id>.dkr.ecr.us-east-1.amazonaws.com/bookstore-api-gateway-service:latest
        ports:
        - containerPort: 8765
        envFrom:
        - configMapRef:
            name: bookstore-config
        env:
        - name: SERVER_PORT
          value: "8765"
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8765
          initialDelaySeconds: 30
          periodSeconds: 10
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: bookstore-api-gateway
  namespace: bookstore
spec:
  selector:
    app: bookstore-api-gateway
  ports:
  - port: 8765
    targetPort: 8765
  type: ClusterIP
```

#### Ingress (ALB)
```yaml
# k8s/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: bookstore-ingress
  namespace: bookstore
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
spec:
  rules:
  - http:
      paths:
      - path: /api/
        pathType: Prefix
        backend:
          service:
            name: bookstore-api-gateway
            port:
              number: 8765
      - path: /
        pathType: Prefix
        backend:
          service:
            name: bookstore-frontend
            port:
              number: 80
```

### Step 4 — Deploy to Kubernetes

```bash
# Apply all manifests
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/

# Check pods
kubectl get pods -n bookstore

# Check services
kubectl get services -n bookstore

# Check ingress (get ALB URL)
kubectl get ingress -n bookstore
```

### Step 5 — Scale Services

```bash
# Scale catalog service to 3 instances
kubectl scale deployment bookstore-catalog-service \
  --replicas=3 -n bookstore

# Auto-scale based on CPU
kubectl autoscale deployment bookstore-catalog-service \
  --cpu-percent=70 --min=2 --max=10 -n bookstore
```

### Step 6 — Rolling Updates

```bash
# Update image
kubectl set image deployment/bookstore-catalog-service \
  catalog-service=<account-id>.dkr.ecr.us-east-1.amazonaws.com/bookstore-catalog-service:v2 \
  -n bookstore

# Check rollout status
kubectl rollout status deployment/bookstore-catalog-service -n bookstore

# Rollback if needed
kubectl rollout undo deployment/bookstore-catalog-service -n bookstore
```

---

## Multi-Instance Readiness Checklist

| Concern | Status | Notes |
|---|---|---|
| Unique Consul instance ID | ✅ | `${spring.application.name}:${random.value}` |
| Stateless JWT auth | ✅ | No session state |
| JDBC OAuth2 token store | ✅ | Tokens shared via MySQL |
| JDBC registered client store | ✅ | Client config shared via MySQL |
| Load balancer (gateway) | ✅ | `lb://` with Spring Cloud LoadBalancer |
| Database connection pooling | ✅ | HikariCP |
| Image storage | ⚠️ | Use S3 + CloudFront for production |
| Flyway migrations | ✅ | DB-level locking prevents duplicates |

---

## Environment Variables Reference

| Variable | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | `local`, `docker`, or `kubernetes` |
| `DB_HOST` | `bookstore-mysql-db` | MySQL hostname |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `bookstore_db` | Database name |
| `DB_USER` | `bookstoreDBA` | Database user |
| `DB_PASSWORD` | `PaSSworD` | Database password |
| `CONSUL_HOST` | `bookstore-consul-discovery` | Consul hostname |
| `CONSUL_PORT` | `8500` | Consul port |
| `ZIPKIN_HOST` | `localhost` | Zipkin hostname |
| `OAUTH2_ISSUER_URI` | `http://localhost:4001` | JWT issuer URI |
| `STRIPE_SECRET_KEY` | `sk_test_placeholder` | Stripe API key |
| `JWT_KEYSTORE_PASSWORD` | `devdcorespass` | JWT keystore password |
