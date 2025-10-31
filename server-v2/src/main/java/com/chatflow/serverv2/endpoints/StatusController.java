package com.chatflow.serverv2.endpoints;

import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

  @GetMapping("/health")
  public Map<String, Object> health() {
    Map<String, Object> result = new HashMap<>();
    result.put("status", "UP");
    result.put("timestamp", java.time.Instant.now().toString());
    return result;
  }
}
