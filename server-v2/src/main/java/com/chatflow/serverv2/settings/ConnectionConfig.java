package com.chatflow.serverv2.settings;

import com.chatflow.serverv2.handlers.MessageHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;


@Configuration
@EnableWebSocket
public class ConnectionConfig implements WebSocketConfigurer {


  @Autowired
  private MessageHandler messageHandler;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(messageHandler, "/chat/*")
        .setAllowedOrigins("*");
  }
}
