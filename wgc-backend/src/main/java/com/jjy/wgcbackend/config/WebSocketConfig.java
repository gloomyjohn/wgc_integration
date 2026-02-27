package com.jjy.wgcbackend.config;

import com.jjy.wgcbackend.handler.SimulationWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Autowired
    private SimulationWebSocketHandler simulationWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 配置前端连接端点 /ws/simulation
        //
        registry.addHandler(simulationWebSocketHandler, "/ws/simulation")
                .setAllowedOrigins("*");
    }
}
