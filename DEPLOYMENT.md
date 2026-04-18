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


---

## AWS EKS Networking — Deep Dive

### VPC Architecture

```
AWS Region (us-east-1)
└── VPC (10.0.0.0/16)
    ├── Public Subnets (ALB, NAT Gateway)
    │   ├── us-east-1a: 10.0.1.0/24
    │   └── us-east-1b: 10.0.2.0/24
    │
    ├── Private Subnets (EKS Nodes, RDS)
    │   ├── us-east-1a: 10.0.10.0/24
    │   └── us-east-1b: 10.0.20.0/24
    │
    └── Internet Gateway → Public Subnets → NAT Gateway → Private Subnets
```

**Rule:** EKS worker nodes and RDS live in **private subnets** — never directly exposed to the internet. Only the ALB lives in public subnets.

---

### Traffic Flow — Step by Step

```
User Browser
    │
    │ HTTPS :443
    ▼
┌─────────────────────────────────────────┐
│  AWS Application Load Balancer (ALB)    │  ← Public Subnet
│  DNS: bookstore.yourdomain.com          │
│  SSL termination here (ACM certificate) │
└──────────────────┬──────────────────────┘
                   │ HTTP :80 (internal)
                   │ Routes:
                   │  /api/* → API Gateway Service
                   │  /*     → Frontend Service
                   ▼
┌─────────────────────────────────────────┐
│  Kubernetes Ingress Controller          │  ← Private Subnet
│  (AWS Load Balancer Controller)         │
└──────────────────┬──────────────────────┘
                   │
        ┌──────────┴──────────┐
        │                     │
        ▼                     ▼
┌──────────────┐    ┌──────────────────┐
│  Frontend    │    │   API Gateway    │
│  Service     │    │   Service        │
│  ClusterIP   │    │   ClusterIP      │
│  :80         │    │   :8765          │
└──────┬───────┘    └────────┬─────────┘
       │                     │
       │              lb:// (Consul DNS)
       │                     │
       │         ┌───────────┼───────────┐
       │         ▼           ▼           ▼
       │  ┌──────────┐ ┌──────────┐ ┌──────────┐
       │  │ Account  │ │ Catalog  │ │  Order   │
       │  │ Service  │ │ Service  │ │  Service │
       │  │ ClusterIP│ │ ClusterIP│ │ ClusterIP│
       │  └────┬─────┘ └────┬─────┘ └────┬─────┘
       │       │             │             │
       └───────┴─────────────┴─────────────┘
                             │
                    ┌────────▼────────┐
                    │   AWS RDS       │  ← Private Subnet
                    │   MySQL 8       │
                    │   Multi-AZ      │
                    └─────────────────┘
```

---

### Kubernetes Networking Layers

#### Layer 1 — Pod Network (CNI)
EKS uses **AWS VPC CNI** plugin. Each pod gets a real VPC IP address from the subnet CIDR.

```
Node 1 (10.0.10.5)
├── Pod: account-service-abc  → IP: 10.0.10.20
├── Pod: catalog-service-xyz  → IP: 10.0.10.21
└── Pod: api-gateway-def      → IP: 10.0.10.22

Node 2 (10.0.20.5)
├── Pod: account-service-ghi  → IP: 10.0.20.20
└── Pod: catalog-service-jkl  → IP: 10.0.20.21
```

Pods can talk to each other directly using their VPC IPs — no NAT needed.

#### Layer 2 — Service Network (kube-proxy)
Kubernetes Services get a stable **ClusterIP** from the service CIDR (e.g. `172.20.0.0/16`). kube-proxy maintains iptables rules to load balance traffic across pod IPs.

```
bookstore-account-service  → ClusterIP: 172.20.10.1  → Pods: 10.0.10.20, 10.0.20.20
bookstore-catalog-service  → ClusterIP: 172.20.10.2  → Pods: 10.0.10.21, 10.0.20.21
bookstore-api-gateway      → ClusterIP: 172.20.10.3  → Pods: 10.0.10.22
```

Services are resolved by DNS: `bookstore-account-service.bookstore.svc.cluster.local`

#### Layer 3 — Ingress (ALB)
The AWS Load Balancer Controller watches Ingress resources and creates/updates ALB target groups automatically.

```yaml
# How the Ingress maps to ALB target groups:
/api/*  → Target Group: api-gateway pods (10.0.10.22, 10.0.20.x)
/*      → Target Group: frontend pods   (10.0.10.x, 10.0.20.x)
```

---

### Service Discovery in Kubernetes

In the `kubernetes` profile, services use **Consul** for discovery (same as Docker). Consul runs as a StatefulSet in the cluster.

```yaml
# Consul StatefulSet
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: consul
  namespace: bookstore
spec:
  replicas: 3                    # 3-node Consul cluster for HA
  serviceName: consul
  selector:
    matchLabels:
      app: consul
  template:
    spec:
      containers:
      - name: consul
        image: hashicorp/consul:1.17
        ports:
        - containerPort: 8500    # HTTP API
        - containerPort: 8600    # DNS
        - containerPort: 8300    # Server RPC
        - containerPort: 8301    # Serf LAN
---
apiVersion: v1
kind: Service
metadata:
  name: consul-service
  namespace: bookstore
spec:
  selector:
    app: consul
  ports:
  - name: http
    port: 8500
    targetPort: 8500
  clusterIP: None                # Headless service for StatefulSet
```

Each microservice registers with Consul on startup:
```yaml
spring.cloud.consul:
  host: consul-service           # K8s DNS resolves to Consul pods
  port: 8500
  discovery:
    instanceId: ${spring.application.name}:${random.value}
    prefer-ip-address: true      # Register with pod IP, not hostname
```

The API Gateway resolves `lb://bookstore-catalog-service` by querying Consul, getting all healthy pod IPs, and round-robining requests.

---

### Security Groups

```
┌─────────────────────────────────────────────────────────┐
│  ALB Security Group                                      │
│  Inbound:  0.0.0.0/0 → :443 (HTTPS)                    │
│  Inbound:  0.0.0.0/0 → :80  (HTTP, redirect to HTTPS)  │
│  Outbound: EKS Node SG → :8765, :80                    │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  EKS Node Security Group                                 │
│  Inbound:  ALB SG → :8765, :80 (from ALB only)         │
│  Inbound:  EKS Node SG → all ports (pod-to-pod)        │
│  Outbound: 0.0.0.0/0 (via NAT Gateway)                 │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  RDS Security Group                                      │
│  Inbound:  EKS Node SG → :3306 (MySQL, from nodes only)│
│  Outbound: none                                         │
└─────────────────────────────────────────────────────────┘
```

---

### DNS Resolution Chain

```
Browser requests: https://bookstore.yourdomain.com/api/catalog/products

1. DNS: bookstore.yourdomain.com → ALB DNS (Route53 CNAME)
2. ALB: routes /api/* to API Gateway pod (10.0.10.22:8765)
3. Gateway: resolves lb://bookstore-catalog-service via Consul
4. Consul: returns [10.0.10.21, 10.0.20.21] (catalog pod IPs)
5. Gateway: picks 10.0.10.21, forwards request
6. Catalog pod: queries RDS at <rds-endpoint>:3306
7. Response flows back through the same chain
```

---

### Network Policies (Zero Trust)

Restrict pod-to-pod communication to only what's needed:

```yaml
# k8s/network-policy.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: catalog-service-policy
  namespace: bookstore
spec:
  podSelector:
    matchLabels:
      app: bookstore-catalog-service
  policyTypes:
  - Ingress
  - Egress
  ingress:
  # Only allow traffic from API Gateway
  - from:
    - podSelector:
        matchLabels:
          app: bookstore-api-gateway
    ports:
    - port: 6001
  # Allow traffic from order-service (Feign calls)
  - from:
    - podSelector:
        matchLabels:
          app: bookstore-order-service
    ports:
    - port: 6001
  egress:
  # Allow MySQL
  - to:
    - ipBlock:
        cidr: 10.0.0.0/16    # VPC CIDR (RDS)
    ports:
    - port: 3306
  # Allow Consul
  - to:
    - podSelector:
        matchLabels:
          app: consul
    ports:
    - port: 8500
```

---

### Complete K8s Manifest Deployment Order

```bash
# 1. Infrastructure
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/network-policy.yaml

# 2. Service Discovery
kubectl apply -f k8s/consul.yaml
kubectl wait --for=condition=ready pod -l app=consul -n bookstore --timeout=120s

# 3. Core Services (account first - others depend on JWT keys)
kubectl apply -f k8s/account-service.yaml
kubectl wait --for=condition=available deployment/bookstore-account-service -n bookstore --timeout=120s

# 4. Business Services (parallel)
kubectl apply -f k8s/catalog-service.yaml
kubectl apply -f k8s/order-service.yaml
kubectl apply -f k8s/billing-service.yaml
kubectl apply -f k8s/payment-service.yaml

# 5. Gateway (after all services registered in Consul)
kubectl apply -f k8s/api-gateway.yaml

# 6. Frontend
kubectl apply -f k8s/frontend.yaml

# 7. Ingress (last - exposes everything)
kubectl apply -f k8s/ingress.yaml

# Verify
kubectl get all -n bookstore
kubectl get ingress -n bookstore    # Get ALB URL
```

---

### Port Reference

| Service | Container Port | K8s Service Port | Exposed Via |
|---|---|---|---|
| Frontend | 80 | 80 | ALB Ingress |
| API Gateway | 8765 | 8765 | ALB Ingress |
| Account Service | 4001 | 4001 | ClusterIP only |
| Catalog Service | 6001 | 6001 | ClusterIP only |
| Order Service | 7001 | 7001 | ClusterIP only |
| Billing Service | 5001 | 5001 | ClusterIP only |
| Payment Service | 8001 | 8001 | ClusterIP only |
| Consul | 8500 | 8500 | ClusterIP only |
| Zipkin | 9411 | 9411 | ClusterIP only |
| MySQL (RDS) | 3306 | N/A | RDS endpoint |


---

## Inter-Pod Communication — How Services Talk to Each Other

### The Golden Rule
**Pods never talk to other pods directly by IP.**
They always go through a Kubernetes **Service** which provides:
- Stable DNS name (survives pod restarts)
- Load balancing across all healthy pods
- Health-check based routing (unhealthy pods removed automatically)

---

### Service Exposure Matrix

| Service | Type | Accessible From | DNS Name (inside cluster) |
|---|---|---|---|
| bookstore-frontend | ClusterIP | ALB Ingress only | `bookstore-frontend.bookstore.svc.cluster.local` |
| bookstore-api-gateway | ClusterIP | ALB Ingress only | `bookstore-api-gateway.bookstore.svc.cluster.local` |
| bookstore-account-service | ClusterIP | Gateway + other services | `bookstore-account-service.bookstore.svc.cluster.local` |
| bookstore-catalog-service | ClusterIP | Gateway + order-service | `bookstore-catalog-service.bookstore.svc.cluster.local` |
| bookstore-order-service | ClusterIP | Gateway only | `bookstore-order-service.bookstore.svc.cluster.local` |
| bookstore-billing-service | ClusterIP | Gateway + order-service | `bookstore-billing-service.bookstore.svc.cluster.local` |
| bookstore-payment-service | ClusterIP | Gateway + order-service | `bookstore-payment-service.bookstore.svc.cluster.local` |
| consul-service | ClusterIP (Headless) | All services | `consul-service.bookstore.svc.cluster.local` |
| zipkin-service | ClusterIP | All services | `zipkin-service.bookstore.svc.cluster.local` |
| RDS MySQL | External | All services | `<rds-endpoint>.rds.amazonaws.com` |

---

### Communication Flow Diagram

```
                    ┌─────────────────────────────────────────┐
                    │           ALB (Public)                   │
                    └──────────────┬──────────────────────────┘
                                   │
                    ┌──────────────▼──────────────────────────┐
                    │  Ingress Controller                      │
                    │  /api/* → api-gateway ClusterIP :8765   │
                    │  /*     → frontend ClusterIP :80        │
                    └──────────────┬──────────────────────────┘
                                   │
                    ┌──────────────▼──────────────────────────┐
                    │  API Gateway (ClusterIP)                 │
                    │  Resolves lb:// via Consul               │
                    └──┬──────┬──────┬──────┬──────┬──────────┘
                       │      │      │      │      │
              ┌────────▼─┐ ┌──▼───┐ ┌▼───┐ ┌▼────┐ ┌▼──────┐
              │ Account  │ │Catalog│ │Order│ │Billing│ │Payment│
              │ClusterIP │ │ClusterIP│ │ClusterIP│ │ClusterIP│ │ClusterIP│
              │  :4001   │ │ :6001 │ │:7001│ │:5001 │ │:8001  │
              └──────────┘ └───────┘ └──┬──┘ └──────┘ └───────┘
                                        │
                              ┌─────────┼──────────┐
                              │         │          │
                         ┌────▼───┐ ┌───▼───┐ ┌───▼────┐
                         │Catalog │ │Billing│ │Payment │
                         │Feign   │ │Feign  │ │Feign   │
                         │:6001   │ │:5001  │ │:8001   │
                         └────────┘ └───────┘ └────────┘
                              │         │          │
                              └─────────┴──────────┘
                                        │
                              ┌─────────▼──────────┐
                              │   RDS MySQL :3306   │
                              └────────────────────┘
```

---

### Feign Client Configuration for K8s

Each service uses Feign to call other services. In the `kubernetes` profile, Feign resolves service names via Consul:

```java
// Order service calling Catalog service
@FeignClient(name = "bookstore-catalog-service")
public interface CatalogFeignClient {
    @GetMapping("/product/{productId}")
    GetProductResponse getProduct(@PathVariable String productId);
}
```

Consul resolves `bookstore-catalog-service` → pod IPs → Spring Cloud LoadBalancer picks one.

---

### What Happens When a Pod Restarts

```
Before restart:
  catalog-service Pod A → IP: 10.0.10.21  ← registered in Consul
  catalog-service Pod B → IP: 10.0.20.21  ← registered in Consul

Pod A crashes and restarts:
  catalog-service Pod A → IP: 10.0.10.35  (new IP!)

Consul health check detects:
  10.0.10.21 → FAILING → removed from registry
  10.0.10.35 → PASSING → added to registry

Gateway load balancer:
  Next request → 10.0.20.21 (Pod B, still healthy)
  After Pod A recovers → round-robin between 10.0.10.35 and 10.0.20.21
```

Zero downtime — the ClusterIP Service and Consul handle it automatically.

---

### Consul in Kubernetes — Headless Service

Consul uses a **Headless Service** (no ClusterIP) so each pod is individually addressable:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: consul-service
  namespace: bookstore
spec:
  clusterIP: None          # Headless - no virtual IP
  selector:
    app: consul
  ports:
  - name: http
    port: 8500
  - name: rpc
    port: 8300
  - name: serf-lan
    port: 8301
```

DNS for headless service returns all pod IPs directly:
```
consul-service.bookstore.svc.cluster.local
  → 10.0.10.30 (consul pod 0)
  → 10.0.10.31 (consul pod 1)
  → 10.0.10.32 (consul pod 2)
```

Each microservice connects to any Consul pod — they form a cluster and share state.

---

### Summary — Exposure Rules

```
NEVER expose directly to internet:
  ✗ Account Service
  ✗ Catalog Service
  ✗ Order Service
  ✗ Billing Service
  ✗ Payment Service
  ✗ Consul
  ✗ Zipkin
  ✗ MySQL (RDS - private subnet)

Only expose via ALB Ingress:
  ✓ API Gateway  (/api/*)
  ✓ Frontend     (/*)

Internal ClusterIP (pod-to-pod only):
  ✓ All microservices
  ✓ Consul
  ✓ Zipkin
```
