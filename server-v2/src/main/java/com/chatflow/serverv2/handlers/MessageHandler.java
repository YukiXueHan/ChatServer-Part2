package com.chatflow.serverv2.handlers;

import com.chatflow.serverv2.entities.MessagePayload;
import com.chatflow.serverv2.entities.QueueItem;
import com.chatflow.serverv2.processors.QueuePublisherService;
import com.chatflow.serverv2.helpers.MessageValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler that publishes messages to SQS instead of echoing.
 */
@Component
public class MessageHandler extends TextWebSocketHandler {

  private static final Logger logger = LoggerFactory.getLogger(MessageHandler.class);

  private final ObjectMapper jsonParser = new ObjectMapper();
  private final QueuePublisherService queueService;

  @Value("${server.id:unknown}")
  private String instanceId;

  // Track active sessions per room
  private final Map<String, Map<String, WebSocketSession>> activeRooms = new ConcurrentHashMap<>();

  // Metrics
  private final Counter receivedMessages;
  private final Counter validationFailures;
  private final Counter publishFailures;

  @Autowired
  public MessageHandler(QueuePublisherService queueService, MeterRegistry meterRegistry) {
    this.queueService = queueService;
    this.receivedMessages = Counter.builder("websocket.messages.received")
        .description("Total messages received from clients")
        .register(meterRegistry);
    this.validationFailures = Counter.builder("websocket.validation.errors")
        .description("Messages that failed validation")
        .register(meterRegistry);
    this.publishFailures = Counter.builder("websocket.queue.publish.errors")
        .description("Messages that failed to publish to queue")
        .register(meterRegistry);
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    String roomKey = getRoomIdFromUri(session);
    activeRooms.computeIfAbsent(roomKey, k -> new ConcurrentHashMap<>())
        .put(session.getId(), session);

    logger.info("Session {} connected to room {} | Total rooms: {}",
        session.getId(), roomKey, activeRooms.size());
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    String messageText = message.getPayload();
    receivedMessages.increment();

    MessagePayload payload;

    // Parse JSON
    try {
      payload = jsonParser.readValue(messageText, MessagePayload.class);
    } catch (Exception e) {
      validationFailures.increment();
      sendErrorResponse(session, "INVALID_JSON", "Malformed JSON payload");
      logger.warn("Invalid JSON from session {}: {}", session.getId(), messageText);
      return;
    }

    // Validate
    String validationResult = MessageValidator.validate(payload);
    if (validationResult != null) {
      validationFailures.increment();
      sendErrorResponse(session, "VALIDATION_ERROR", validationResult);
      logger.warn("Validation failed for session {}: {}", session.getId(), validationResult);
      return;
    }

    // Extract metadata
    String roomKey = getRoomIdFromUri(session);
    String ipAddress = getClientIpAddress(session);

    // Create queue message
    QueueItem queueItem = QueueItem.fromMessagePayload(payload, roomKey, instanceId, ipAddress);

    // Publish to SQS
    boolean success = queueService.publishMessage(queueItem);

    if (success) {
      // Send acknowledgment to sender
      sendAckResponse(session, payload);
      logger.debug("Message from user {} published to room {}",
          payload.getUserId(), roomKey);
    } else {
      publishFailures.increment();
      sendErrorResponse(session, "QUEUE_ERROR", "Failed to publish message to queue");
      logger.error("Failed to publish message from session {} to queue", session.getId());
    }
  }

  /**
   * Sends an acknowledgment response to the client
   */
  private void sendAckResponse(WebSocketSession session, MessagePayload originalPayload) {
    try {
      MessagePayload ackPayload = new MessagePayload();
      ackPayload.setUserId(originalPayload.getUserId());
      ackPayload.setUsername(originalPayload.getUsername());
      ackPayload.setMessage(originalPayload.getMessage());
      ackPayload.setTimestamp(originalPayload.getTimestamp());
      ackPayload.setMessageType(originalPayload.getMessageType());
      ackPayload.setServerTimestamp(Instant.now().toString());
      ackPayload.setStatus("OK");

      String ackJson = jsonParser.writeValueAsString(ackPayload);
      session.sendMessage(new TextMessage(ackJson));
    } catch (Exception e) {
      logger.error("Failed to send acknowledgment to session {}: {}",
          session.getId(), e.getMessage());
    }
  }

  /**
   * Sends an error message to the client
   */
  private void sendErrorResponse(WebSocketSession session, String code, String message) {
    try {
      MessagePayload errorPayload = new MessagePayload();
      errorPayload.setStatus("ERROR");
      errorPayload.setErrorCode(code);
      errorPayload.setErrorMessage(message);
      errorPayload.setServerTimestamp(Instant.now().toString());

      String errorJson = jsonParser.writeValueAsString(errorPayload);
      session.sendMessage(new TextMessage(errorJson));
    } catch (Exception ex) {
      logger.error("Failed to send error response to session {}: {}",
          session.getId(), ex.getMessage());
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String roomKey = getRoomIdFromUri(session);
    Map<String, WebSocketSession> roomConnections = activeRooms.get(roomKey);

    if (roomConnections != null) {
      roomConnections.remove(session.getId());
      if (roomConnections.isEmpty()) {
        activeRooms.remove(roomKey);
      }
    }

    logger.info("Session {} disconnected from room {} (code: {}, reason: {}) | Total rooms: {}",
        session.getId(), roomKey, status.getCode(), status.getReason(), activeRooms.size());
  }

  /**
   * Extracts room ID from WebSocket session URI
   */
  private String getRoomIdFromUri(WebSocketSession session) {
    if (session == null || session.getUri() == null) {
      return "unknown";
    }
    String path = session.getUri().getPath();
    if (path == null || path.isEmpty()) {
      return "unknown";
    }
    String[] segments = path.split("/");
    return segments.length > 0 ? segments[segments.length - 1] : "unknown";
  }

  /**
   * Extracts client IP from WebSocket session
   */
  private String getClientIpAddress(WebSocketSession session) {
    try {
      InetSocketAddress remoteAddr = session.getRemoteAddress();
      if (remoteAddr != null) {
        return remoteAddr.getAddress().getHostAddress();
      }
    } catch (Exception e) {
      logger.debug("Failed to extract client IP: {}", e.getMessage());
    }
    return "unknown";
  }

  /**
   * Gets current connection statistics
   */
  public String getConnectionStats() {
    int totalConnections = activeRooms.values().stream()
        .mapToInt(Map::size)
        .sum();
    return String.format("Active rooms: %d, Total sessions: %d",
        activeRooms.size(), totalConnections);
  }
}
