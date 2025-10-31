package com.chatflow.consumer.connection;

import com.chatflow.consumer.processor.MessageDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket handler for consumer application.
 * Clients connect here to receive broadcasted messages.
 */
@Component
public class WebSocketManager extends TextWebSocketHandler {

  private static final Logger logger = LoggerFactory.getLogger(WebSocketManager.class);

  private final MessageDistributor distributor;

  @Autowired
  public WebSocketManager(MessageDistributor distributor) {
    this.distributor = distributor;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    String roomId = extractRoomIdFromSession(session);
    distributor.addSessionToRoom(roomId, session);

    logger.info("Consumer session {} connected to room {}", session.getId(), roomId);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    distributor.removeSession(session);

    logger.info("Consumer session {} disconnected (code: {}, reason: {})",
        session.getId(), status.getCode(), status.getReason());
  }

  /**
   * Extracts room ID from WebSocket session URI
   */
  private String extractRoomIdFromSession(WebSocketSession session) {
    if (session == null || session.getUri() == null) {
      return "unknown";
    }
    String path = session.getUri().getPath();
    if (path == null || path.isEmpty()) {
      return "unknown";
    }
    String[] parts = path.split("/");
    return parts.length > 0 ? parts[parts.length - 1] : "unknown";
  }
}