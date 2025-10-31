package com.chatflow.consumer.api;

import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

  @GetMapping("/health")
  public Map<String, Object> health() {
    Map<String, Object> response = new HashMap<>();
    response.put("status", "UP");
    response.put("timestamp", java.time.Instant.now().toString());
    return response;
  }
}