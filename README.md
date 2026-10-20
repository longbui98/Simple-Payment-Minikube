# Simple Payment Minikube

A Java-based microservices demo for a simple payment workflow deployed to Minikube/Kubernetes. The project models a minimal payment system with a public API, a payment processing service, and a wallet service backed by PostgreSQL.

## Overview

The application demonstrates how multiple services can collaborate to handle a transaction:

1. A client submits a payment request through the public API.
2. The payment API forwards the request to the processor service.
3. The processor validates the payment and wallet state.
4. The payment is recorded in the database.
5. The wallet service updates the sender and receiver balances.
6. The request response is returned to the caller.

This architecture is intentionally simple, but it follows the structure of many real-world distributed payment systems.

## Architecture

```text
Client
  |
  v
Payment-API (port 8080)
  |
  v
Payment-Processor2 (port 8080)
  |        \
  |         \____ calls /wallets/validate and /wallets/update-balance
  v
Wallet-Service (port 8082)
  |
  v
PostgreSQL (payment-database)
```

## Services

### 1. Payment-API
The public-facing entry point for incoming requests.

Responsibilities:
- Exposes REST endpoints for payment and wallet operations
- Forwards requests to the processor service
- Propagates trace IDs between services

Main class:
- `Payment-API/src/main/java/com/example/processor/demo/PaymentApiApplication.java`

Endpoints:
- `POST /payments`
- `POST /wallets`

### 2. Payment-Processor2
Handles the core payment processing workflow.

Responsibilities:
- Validates payment requests
- Checks wallet balance availability
- Saves payment records
- Calls the wallet service to update balances
- Returns status and error messages

Main class:
- `Payment-Processor2/src/main/java/com/example/processor/PaymentProcessorApplication.java`

Key controller:
- `Payment-Processor2/src/main/java/com/example/processor/controller/ProcessorController.java`

Important behavior:
- Validates using `ValidatePaymentRequest`
- Uses `RestClient` to call the wallet service
- Stores payment records in a JPA entity named `Payment`

### 3. Wallet-Service
Owns wallet state and balance logic.

Responsibilities:
- Create wallet records
- Retrieve wallet balance by user
- Validate sender/recipient wallet balances
- Update sender and receiver balances atomically

Main class:
- `Wallet-Service/src/main/java/com/example/wallet/WalletApplication.java`

Key controller:
- `Wallet-Service/src/main/java/com/example/wallet/controller/WalletController.java`

Key service:
- `Wallet-Service/src/main/java/com/example/wallet/service/WalletService.java`

Wallet entity:
- `Wallet-Service/src/main/java/com/example/wallet/data/Wallet.java`

## Data model

### Payment
Fields:
- `id` (UUID)
- `amount`
- `currency`
- `status`
- `fromUsrId`
- `toUsrId`
- `createdAt`
- `updatedAt`

### Wallet
Fields:
- `userId`
- `balance`
- `createdAt`
- `updatedAt`

### Persistence
The project uses PostgreSQL as the data store for both payment and wallet records.

Database service name inside Kubernetes:
- `payment-database`

Connection pattern:
- `jdbc:postgresql://payment-database:5432/postgres`

Default credentials:
- username: `postgres`
- password: `postgres`

## Technology stack

- Java 21
- Maven multi-module build
- Spring Boot
- Spring Web
- Spring Data JPA
- PostgreSQL
- Docker
- Kubernetes / Minikube
- GitHub Actions
- Logback with Logstash encoder

## Repository structure

```text
.
├── .github/
│   └── workflows/
│       └── payment-ci-cd.yml
├── Payment-API/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
├── Payment-Processor2/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
├── Wallet-Service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
├── k8s/
│   ├── payment-apps.yaml
│   ├── payment-database.yaml
│   ├── payment-services.yaml
│   ├── elk-stack.yaml
│   └── fluent-bit.yaml
├── pom.xml
├── qodana.yaml
└── README.md
```

## Build and run locally

### Prerequisites
- Java 21+
- Maven
- Docker
- Minikube
- kubectl

### Build the services

```bash
cd Payment-API && mvn clean package
cd ../Payment-Processor2 && mvn clean package
cd ../Wallet-Service && mvn clean package
```

### Build Docker images

```bash
docker build -t payment-api:1.0 ./Payment-API
docker build -t payment-processor:1.0 ./Payment-Processor2
docker build -t wallet-service:1.0 ./Wallet-Service
```

### Load images into Minikube

```bash
minikube image load payment-api:1.0
minikube image load payment-processor:1.0
minikube image load wallet-service:1.0
```

### Deploy to Kubernetes

```bash
kubectl apply -f k8s/payment-database.yaml
kubectl apply -f k8s/payment-apps.yaml
kubectl apply -f k8s/payment-services.yaml
```

## Configuration

### Payment API
`Payment-API/src/main/resources/application.properties`

```properties
spring.application.name=payment-api
server.port=8080
processor.url=http://payment-processor
```

### Wallet Service
`Wallet-Service/src/main/resources/application.properties`

```properties
spring.application.name=wallet-service
server.port=8082

spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://payment-db:5432/postgres}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:postgres}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:postgres}
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

## Kubernetes manifests

The project includes separate Kubernetes deployment files for:

- `payment-database.yaml`: PostgreSQL database deployment and service
- `payment-apps.yaml`: API, processor, and wallet deployments
- `payment-services.yaml`: NodePort and ClusterIP service definitions

These resources expose:
- `payment-api` through NodePort on `30080`
- `payment-processor` internally via ClusterIP
- `wallet-service` internally via ClusterIP
- `payment-database` internally via ClusterIP on port `5432`

## CI/CD

The repo includes a GitHub Actions workflow at:

- `.github/workflows/payment-ci-cd.yml`

It detects changes per service and deploys only the impacted service to Minikube using a self-hosted runner.

The workflow covers:
- API deployment
- Processor deployment
- Wallet deployment

## REST API behavior

### Payment API
`POST /payments`
- Accepts payment request body
- Forwards the request to payment-processor

Example request:

```json
{
  "paymentId": null,
  "amount": 100.00,
  "currency": "USD",
  "fromUsrId": "alice",
  "toUsrId": "bob"
}
```

### Wallet creation
`POST /wallets`
- Creates a wallet for a user

Example request:

```json
{
  "userId": "alice",
  "balance": 1000.00
}
```

### Wallet balance
`GET /wallets/{userId}`
- Returns wallet balance for a user

### Wallet validation
`GET /wallets/validate?fromUsrId=alice&toUsrId=bob&amount=50.00`
- Validates that the sender has enough money and the recipient wallet exists

### Wallet update
`POST /wallets/update-balance`
- Updates sender and recipient balances in one transaction

## Notes and observations

- The repo is a demo system and not a production-grade financial platform.
- Some service module configurations appear to have version inconsistencies across files; the root project uses Spring Boot 3.2.5, while some service POMs are set to Spring Boot 4.1.1.
- The code uses simple in-memory style validation and transaction patterns, suitable for educational or demo scenarios.
- The project is designed to be run in a local Kubernetes environment via Minikube.

## Useful commands

```bash
# Check pods
kubectl get pods

# Check services
kubectl get svc

# View logs for processor
kubectl logs deployment/payment-processor

# View logs for wallet
kubectl logs deployment/wallet-service
```

## License

This project does not appear to include a dedicated license file in the repository snapshot reviewed here.

## Summary

This project is a lightweight microservice sample for handling payments and wallet transfers using Java, Spring Boot, PostgreSQL, Docker, and Kubernetes. It is meant to demonstrate how to structure service-to-service communication, persistence, validation, and deployment in a local cluster environment.
