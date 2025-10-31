package com.chatflow.serverv2.processors;

import com.chatflow.serverv2.entities.QueueItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for publishing messages to AWS SQS.
 * Implements connection pooling and circuit breaker pattern.
 */
@Service
public class QueuePublisherService {

  private static final Logger log = LoggerFactory.getLogger(QueuePublisherService.class);
  private static final int CONNECTION_POOL_SIZE = 10;
  private static final int MAX_ATTEMPTS = 3;
  private static final long BREAKER_TIMEOUT_MS = 30000;

  @Value("${aws.region:us-west-2}")
  private String region;

  @Value("${sqs.queue.url.prefix}")
  private String urlPrefix;

  private final ObjectMapper jsonMapper = new ObjectMapper();
  private final BlockingQueue<SqsClient> connectionPool = new LinkedBlockingQueue<>();
  private final AtomicInteger errorCounter = new AtomicInteger(0);
  private volatile long lastErrorTimestamp = 0;
  private volatile boolean breakerActive = false;

  private Counter publishedCount;
  private Counter failureCount;

  public QueuePublisherService(MeterRegistry meterRegistry) {
    this.publishedCount = Counter.builder("sqs.messages.published")
        .description("Total messages published to SQS")
        .register(meterRegistry);
    this.failureCount = Counter.builder("sqs.publish.failures")
        .description("Failed SQS publish attempts")
        .register(meterRegistry);
  }

  @PostConstruct
  public void initialize() {
    log.info("Initializing SQS client pool with size: {}", CONNECTION_POOL_SIZE);

    for (int idx = 0; idx < CONNECTION_POOL_SIZE; idx++) {
      try {
        SqsClient sqsConnection = SqsClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
        connectionPool.offer(sqsConnection);
      } catch (Exception ex) {
        log.error("Failed to create SQS client {}: {}", idx, ex.getMessage());
      }
    }

    log.info("SQS client pool initialized with {} clients", connectionPool.size());
  }

  @PreDestroy
  public void shutdown() {
    log.info("Shutting down SQS client pool");
    connectionPool.forEach(SqsClient::close);
    connectionPool.clear();
  }

  /**
   * Publishes a message to the appropriate room queue.
   *
   * @param item The queue item to publish
   * @return true if successful, false otherwise
   */
  public boolean publishMessage(QueueItem item) {
    // Check circuit breaker
    if (isBreakerActive()) {
      log.warn("Circuit breaker is OPEN, rejecting message publish");
      return false;
    }

    SqsClient sqsConnection = null;
    try {
      // Get client from pool
      sqsConnection = connectionPool.poll(5, TimeUnit.SECONDS);
      if (sqsConnection == null) {
        log.error("Failed to obtain SQS client from pool");
        failureCount.increment();
        return false;
      }

      // Determine queue URL based on room ID
      String targetQueueUrl = buildQueueUrl(item.getRoomId());

      // Serialize message
      String jsonBody = jsonMapper.writeValueAsString(item);

      // Publish with retry logic
      boolean success = sendWithRetry(sqsConnection, targetQueueUrl, jsonBody, item.getMessageId());

      if (success) {
        publishedCount.increment();
        resetBreaker();
      } else {
        failureCount.increment();
        recordError();
      }

      return success;

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while waiting for SQS client");
      failureCount.increment();
      return false;
    } catch (Exception e) {
      log.error("Error publishing message to SQS: {}", e.getMessage(), e);
      failureCount.increment();
      recordError();
      return false;
    } finally {
      // Return client to pool
      if (sqsConnection != null) {
        connectionPool.offer(sqsConnection);
      }
    }
  }

  /**
   * Publishes message with retry logic
   */
  private boolean sendWithRetry(SqsClient sqsConnection, String targetQueueUrl,
      String jsonBody, String msgId) {
    for (int attemptNum = 1; attemptNum <= MAX_ATTEMPTS; attemptNum++) {
      try {
        SendMessageRequest request = SendMessageRequest.builder()
            .queueUrl(targetQueueUrl)
            .messageBody(jsonBody)
            .messageGroupId(msgId.substring(0, 8)) // For FIFO grouping
            .messageDeduplicationId(msgId)
            .build();

        SendMessageResponse response = sqsConnection.sendMessage(request);

        log.debug("Message published to queue {} with ID: {}",
            targetQueueUrl, response.messageId());
        return true;

      } catch (QueueDoesNotExistException e) {
        log.error("Queue does not exist: {}", targetQueueUrl);
        return false; // Don't retry for non-existent queue
      } catch (Exception e) {
        log.warn("Publish attempt {} failed: {}", attemptNum, e.getMessage());
        if (attemptNum == MAX_ATTEMPTS) {
          log.error("All retry attempts exhausted for queue: {}", targetQueueUrl);
          return false;
        }
        // Exponential backoff
        try {
          Thread.sleep((long) Math.pow(2, attemptNum) * 100);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
    }
    return false;
  }

  /**
   * Gets the queue URL for a given room ID
   */
  private String buildQueueUrl(String roomId) {
    return urlPrefix + "room-" + roomId + ".fifo";
  }

  /**
   * Records a failure and checks if circuit breaker should open
   */
  private void recordError() {
    int errors = errorCounter.incrementAndGet();
    lastErrorTimestamp = System.currentTimeMillis();

    if (errors >= 10) {
      breakerActive = true;
      log.error("Circuit breaker OPENED after {} failures", errors);
    }
  }

  /**
   * Resets circuit breaker on successful operation
   */
  private void resetBreaker() {
    if (breakerActive || errorCounter.get() > 0) {
      breakerActive = false;
      errorCounter.set(0);
      log.info("Circuit breaker CLOSED - service recovered");
    }
  }

  /**
   * Checks if circuit breaker is open
   */
  private boolean isBreakerActive() {
    if (!breakerActive) {
      return false;
    }

    // Check if timeout has passed
    long elapsedTime = System.currentTimeMillis() - lastErrorTimestamp;
    if (elapsedTime > BREAKER_TIMEOUT_MS) {
      log.info("Circuit breaker timeout passed, attempting to close");
      breakerActive = false;
      errorCounter.set(0);
      return false;
    }

    return true;
  }

  /**
   * Gets current pool statistics
   */
  public String getPoolStats() {
    return String.format("Pool: %d/%d available, Failures: %d, Circuit: %s",
        connectionPool.size(), CONNECTION_POOL_SIZE, errorCounter.get(),
        breakerActive ? "OPEN" : "CLOSED");
  }
}
