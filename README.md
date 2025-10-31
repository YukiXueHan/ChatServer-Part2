## ChatServer Part 2

ChatServer Part 2 implements an end-to-end real-time chat workload using WebSockets on top of AWS SQS. It comprises a traffic-driving `ChatClient`, a Spring Boot producer service (`server-v2`) that accepts WebSocket traffic and pushes to SQS FIFO queues, and a Spring Boot consumer service (`consumer`) that fans messages back out to connected clients.

### Project Layout

- `server-v2/` — WebSocket ingress tier; validates chat payloads and publishes them to room-scoped FIFO queues via `SQSNotificationService` with retry, pooling, and circuit breaker safeguards.
- `consumer/` — SQS worker pool; long-polls room queues in parallel, rehydrates `TaskMessage` payloads, and broadcasts as `BroadcastEvent` frames to subscribed WebSocket sessions.
- `deployment/` — AWS helper scripts for load balancer and SQS provisioning.
- `monitoring/` — CloudWatch dashboard template for operational telemetry.

### High-Level Architecture

1. **ChatClient** (load generator) opens WebSocket connections per target room, feeds synthesized messages through a `BlockingQueue`, and spawns `ChatWorker` threads to send messages and track acknowledgments.
2. **Producer (`server-v2`)** receives `ConversationMessage` payloads, validates fields, stamps metadata (room id, server id, client ip), and publishes a `TaskMessage` to the FIFO queue for that room. Successful publishes trigger an acknowledgment back to the originating WebSocket; failures return an error frame.
3. **AWS SQS** stores room-specific chat events with ordering guarantees (`messageGroupId`) and deduplication (`messageDeduplicationId`).
4. **Consumer service** runs a fixed thread pool defined by `consumer.thread.count`. Each worker long-polls a partition of room queues, converts SQS messages into `BroadcastEvent` JSON, and sends them to active WebSocket subscribers. Successful broadcasts delete the SQS message; failures leave it for retry.

### Configuration

Key runtime parameters live in each module's `src/main/resources/application.properties`:

- `aws.region` — AWS deployment region (`us-west-2` default) used for SQS clients.
- `sqs.queue.url.prefix` — fully qualified queue prefix (e.g., `https://sqs.us-west-2.amazonaws.com/<account>/chatflow-`). Producers append `room-{id}.fifo`; consumers iterate room IDs within the configured range.
- `sqs.rooms.start` / `sqs.rooms.end` (consumer) — inclusive room ID bounds polled by the worker pool.
- `consumer.thread.count` — number of SQS polling threads.
- `server.id` — identifier injected into `TaskMessage` for observability.

Override these values via environment variables or Spring Boot property sources when deploying to different environments.

### Building and Running

Both services are Maven-based Spring Boot applications.

```bash
# From repo root
mvn clean package
```

Prerequisites:

- Java 17+
- AWS credentials with permissions to manage SQS queues referenced by `sqs.queue.url.prefix`
- Optional: AWS CLI to run helper scripts in `deployment/`

### Observability

- Micrometer metrics in `server-v2` capture WebSocket intake and SQS publish health (`websocket.messages.received`, `sqs.publish.failures`).
- The consumer tracks successful vs failed broadcasts and exposes `/actuator/health`, `/actuator/metrics`, and `/actuator/info` for basic visibility.
- `monitoring/cloudwatch.json` provides a starting dashboard for queue depth, publish rates, and error counts.

### Message Metrics Workflow

- The load generator records per-message latency and success/failure outcomes.
- `PerformanceAnalyzer` computes latency percentiles, throughput per room, and message type distribution, printing summaries to the console.

### Deployment Notes

- Use `deployment/sqs-setup.sh` to provision FIFO queues with the expected naming convention before starting services.
- Ensure security groups, IAM roles, and ALB routes align with WebSocket traffic requirements (see `deployment/alb-setup.sh`).
