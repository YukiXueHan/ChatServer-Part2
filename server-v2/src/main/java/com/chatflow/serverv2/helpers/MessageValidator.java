package com.chatflow.serverv2.helpers;

import com.chatflow.serverv2.entities.MessagePayload;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.apache.commons.lang3.StringUtils;


/**
 * Validation logic for MessagePayload payloads.
 * Returns null if OK, otherwise an error message string describing the problem.
 */
public class MessageValidator {

  public static String validate(MessagePayload payload) {
    if (payload == null) return "payload missing";

    // userId: accept numeric string or numeric JSON value mapped to String by Jackson
    if (StringUtils.isBlank(payload.getUserId())) return "userId missing";
    int userNum;
    try {
      // strip quotes/whitespace, in case client sends e.g. " 123 "
      String uidValue = payload.getUserId().trim();
      userNum = Integer.parseInt(uidValue);
    } catch (NumberFormatException e) {
      return "userId must be an integer";
    }
    if (userNum < 1 || userNum > 100000) return "userId out of range (1-100000)";

    // username
    if (StringUtils.isBlank(payload.getUsername())) return "username missing";
    if (!payload.getUsername().matches("^[A-Za-z0-9]{3,20}$"))
      return "username invalid (3-20 alphanumeric)";

    // message
    if (StringUtils.isBlank(payload.getMessage())) return "message missing";
    int msgLength = payload.getMessage().length();
    if (msgLength < 1 || msgLength > 500) return "message length invalid (1-500 chars)";

    // timestamp - ISO-8601
    if (StringUtils.isBlank(payload.getTimestamp())) return "timestamp missing";
    try {
      OffsetDateTime.parse(payload.getTimestamp());
    } catch (DateTimeParseException e) {
      return "timestamp not valid ISO-8601";
    }

    // messageType
    if (StringUtils.isBlank(payload.getMessageType())) return "messageType missing";
    String msgType = payload.getMessageType();
    if (!(msgType.equals("TEXT") || msgType.equals("JOIN") || msgType.equals("LEAVE")))
      return "messageType invalid (must be TEXT|JOIN|LEAVE)";

    return null; // OK
  }
}
