# Simple Payment Minikube

A Java-based microservices demo for a simple payment workflow running on Minikube and Kubernetes. The project demonstrates a minimal distributed system with:
- a public API entry point
- a payment-processing service
- a wallet service
- PostgreSQL persistence
- LocalStack/SQS for asynchronous messaging

This repository is structured as a multi-service Java project with Docker and Kubernetes manifests for local cluster deployment.

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
  |         \-- calls wallet validation and balance updates
  v
Wallet-Service (port 8082)
  |
  v
PostgreSQL (payment-database)
  |
  v
LocalStack (SQS)
```

## Services

### 1. Payment-API
Entry point for client requests.

Responsibilities:
- accepts payment requests
- accepts wallet creation requests
- forwards requests to the payment processor
- propagates trace IDs between services

Main class:
- `Payment-API/src/main/java/com/example/processor/demo/PaymentApiApplication.java`

Endpoints:
- `POST /payments`
- `POST /wallets`

Example payment request:

```json
{
  "paymentId": null,
  "amount": 100.00,
  "currency": "USD",
  "fromUsrId": "alice",
  "toUsrId": "bob"
}
```

### 2. Payment-Processor2
Handles the core payment workflow.

Responsibilities:
- validates payment requests
- checks wallet balance and account existence
- persists payment records
- calls the wallet service to update balances
- returns transaction results and error messages

Main class:
- `Payment-Processor2/src/main/java/com/example/processor/PaymentProcessorApplication.java`

Key controller:
- `Payment-Processor2/src/main/java/com/example/processor/controller/ProcessorController.java`

Important behavior:
- uses `RestClient` to call the wallet service
- persists payment data using JPA and PostgreSQL
- integrates with SQS through Spring Cloud AWS

### 3. Wallet-Service
Owns wallet state and balance logic.

Responsibilities:
- creates wallet records
- retrieves wallet balance by user
- validates sender balance before transfer
- updates sender and recipient balances atomically

Main class:
- `Wallet-Service/src/main/java/com/example/wallet/WalletApplication.java`

Key controller:
- `Wallet-Service/src/main/java/com/example/wallet/controller/WalletController.java`

Key service:
- `Wallet-Service/src/main/java/com/example/wallet/service/WalletService.java`

Endpoints:
- `POST /wallets`
- `GET /wallets/{userId}`
- `GET /wallets/validate`
- `POST /wallets/update-balance`

## Repository structure

```text
.
├── .github/
│   └── workflows/
│       ├── payment-ci-cd.yml
│       └── qodana_code_quality.yml
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
│   ├── elk-stack.yaml
│   ├── fluent-bit.yaml
│   ├── localstack.yaml
│   ├── payment-apps.yaml
│   ├── payment-database.yaml
│   └── payment-services.yaml
├── pom.xml
├── qodana.yaml
├── README.md
└── .idea/
```

## Technology stack

- Java 21
- Maven
- Spring Boot
- Spring Web
- Spring Data JPA
- PostgreSQL
- Docker
- Kubernetes / Minikube
- LocalStack
- AWS SQS
- GitHub Actions
- Logback + Logstash encoder

## Kubernetes and infrastructure

The project includes Kubernetes manifests under `k8s/`:

- `payment-database.yaml`: PostgreSQL deployment and service
- `payment-apps.yaml`: API, processor, and wallet deployments
- `payment-services.yaml`: NodePort and ClusterIP services
- `localstack.yaml`: local SQS mock service
- `elk-stack.yaml`: Elastic stack for logs
- `fluent-bit.yaml`: log shipping for container logs

Service exposure:
- `payment-api` is exposed via NodePort on `30080`
- `payment-processor` is exposed internally via ClusterIP
- `wallet-service` is exposed internally via ClusterIP
- `payment-database` is exposed internally on port `5432`
- `localstack` is exposed internally on port `4566`

## Configuration

### Payment API
`Payment-API/src/main/resources/application.properties`

```properties
spring.application.name=payment-api
server.port=8080
processor.url=http://payment-processor
```

### Payment Processor
`Payment-Processor2/src/main/resources/application.properties`

```properties
spring.application.name=payment-processor
server.port=8080

spring.datasource.url=${DB_URL:jdbc:postgresql://10.244.0.9:5432/postgres}
spring.datasource.username=${POSTGRES_USER:postgres}
spring.datasource.password=${POSTGRES_PASSWORD:postgres}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

wallet.url=http://wallet-service

sqs.endpoint=${SQS_ENDPOINT:http://localhost:4566}
sqs.queue.name=${SQS_QUEUE_NAME:transaction_wallet}

spring.cloud.aws.region.static=us-east-1
spring.cloud.aws.credentials.access-key=test
spring.cloud.aws.credentials.secret-key=test
spring.cloud.aws.sqs.endpoint=${sqs.endpoint}
spring.cloud.aws.sqs.queue-not-found-strategy=CREATE
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

sqs.endpoint=${SQS_ENDPOINT:http://localhost:4566}
sqs.queue.name=${SQS_QUEUE_NAME:transaction_wallet}
```

## CI/CD

The repo includes a GitHub Actions workflow for service-aware deployment:

- `.github/workflows/payment-ci-cd.yml`

Behavior:
- detects which service changed
- builds the impacted service image
- deploys the updated image to the matching Kubernetes deployment
- handles API, processor, and wallet service workflows separately

## Local development

### Prerequisites
- Java 21
- Maven
- Docker
- Minikube
- kubectl

### Build services

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

### Load images to Minikube

```bash
minikube image load payment-api:1.0
minikube image load payment-processor:1.0
minikube image load wallet-service:1.0
```

### Deploy to Kubernetes

```bash
kubectl apply -f k8s/payment-database.yaml
kubectl apply -f k8s/localstack.yaml
kubectl apply -f k8s/payment-apps.yaml
kubectl apply -f k8s/payment-services.yaml
```

## Notes

- This project is designed as a demo or learning project rather than a production-grade financial platform.
- The architecture intentionally keeps service boundaries simple for understanding and experimentation.
- The repository includes both local development and Kubernetes deployment patterns.
- Some configuration and module naming details may evolve; verify them before using a root multi-module build.

## Useful commands

```bash
# Check pods
kubectl get pods

# Check services
kubectl get svc

# View logs
kubectl logs deployment/payment-processor
kubectl logs deployment/wallet-service
kubectl logs deployment/payment-api
```

## License

This repository does not currently include a dedicated license file in the project snapshot reviewed here.
