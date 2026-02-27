package com.jjy.wgcbackend.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;


@Component
public class SimulationWebSocketHandler extends TextWebSocketHandler {
    // 线程安全的列表，用于保存所有连接进来的前端页面（比如多个浏览器同时打开仪表盘）
    private static final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(SimulationWebSocketHandler.class);

    // 1 前端成功建立连接触发
    @Override
    public void afterConnectionEstablished(WebSocketSession session)  {
        sessions.add(session);
        log.info("New connection established: " + session.getId());
        log.info("Number of Connections: " + sessions.size());
    }

    // 2 接收到前端发来的消息触发
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)  {
        // TODO 暂时没有接收消息的需要
        log.info("Received text message: " + message.getPayload());
    }

    // 3 前端断开连接时触发
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status)  {
        sessions.remove(session);
        log.info("Connection closed: " + session.getId());
        log.info("Number of Connections: " + sessions.size());
    }

    // 4 自定义，供service调用，向所有前端广播消息
    public void broadcastMessage(Object payload) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(payload);
            TextMessage message = new TextMessage(jsonMessage);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            }
        } catch (IOException e) {
            log.error("WebSocket Broadcasting Error: " + e.getMessage());
        }
    }
}
