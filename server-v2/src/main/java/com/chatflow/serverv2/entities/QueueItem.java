package com.chatflow.serverv2.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Message format for SQS queue.
 * This is what gets published to the queue and consumed by the consumer application.
 */
public class QueueItem {

  @JsonProperty("messageId")
  private String messageId;

  @JsonProperty("roomId")
  private String roomId;

  @JsonProperty("userId")
  private String userId;

  @JsonProperty("username")
  private String username;

  @JsonProperty("message")
  private String message;

  @JsonProperty("timestamp")
  private String timestamp;

  @JsonProperty("messageType")
  private String messageType; // TEXT|JOIN|LEAVE

  @JsonProperty("serverId")
  private String serverId;

  @JsonProperty("clientIp")
  private String clientIp;

  // Constructors
  public QueueItem() {
    this.messageId = UUID.randomUUID().toString();
  }

  /**
   * Creates a QueueItem from a MessagePayload
   */
  public static QueueItem fromMessagePayload(
      MessagePayload payload,
      String roomId,
      String serverId,
      String clientIp) {

    QueueItem item = new QueueItem();
    item.setRoomId(roomId);
    item.setUserId(payload.getUserId());
    item.setUsername(payload.getUsername());
    item.setMessage(payload.getMessage());
    item.setTimestamp(payload.getTimestamp());
    item.setMessageType(payload.getMessageType());
    item.setServerId(serverId);
    item.setClientIp(clientIp);

    return item;
  }

  // Getters and Setters
  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }

  public String getRoomId() {
    return roomId;
  }

  public void setRoomId(String roomId) {
    this.roomId = roomId;
  }

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

  public String getServerId() {
    return serverId;
  }

  public void setServerId(String serverId) {
    this.serverId = serverId;
  }

  public String getClientIp() {
    return clientIp;
  }

  public void setClientIp(String clientIp) {
    this.clientIp = clientIp;
  }
}
