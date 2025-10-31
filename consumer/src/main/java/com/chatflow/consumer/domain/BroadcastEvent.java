package com.chatflow.consumer.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Message format sent to WebSocket clients.
 */
public class BroadcastEvent {

  @JsonProperty("userId")
  private String userId;

  @JsonProperty("username")
  private String username;

  @JsonProperty("message")
  private String message;

  @JsonProperty("timestamp")
  private String timestamp;

  @JsonProperty("messageType")
  private String messageType;

  @JsonProperty("serverTimestamp")
  private String serverTimestamp;

  @JsonProperty("status")
  private String status;

  /**
   * Creates a BroadcastEvent from a TaskMessage
   */
  public static BroadcastEvent fromTaskMessage(TaskMessage message) {
    BroadcastEvent event = new BroadcastEvent();
    event.setUserId(message.getUserId());
    event.setUsername(message.getUsername());
    event.setMessage(message.getMessage());
    event.setTimestamp(message.getTimestamp());
    event.setMessageType(message.getMessageType());
    event.setServerTimestamp(Instant.now().toString());
    event.setStatus("OK");
    return event;
  }

  // Getters and Setters
  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  public String getMessageType() {
    return messageType;
  }

  public void setMessageType(String messageType) {
    this.messageType = messageType;
  }

  public String getServerTimestamp() {
    return serverTimestamp;
  }

  public void setServerTimestamp(String serverTimestamp) {
    this.serverTimestamp = serverTimestamp;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}