package cn.arorms.fms.server.services;

import cn.arorms.fms.server.dto.FermenterStatusDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WebSocketService {

    public static final String TOPIC = "/topic/fermenter-status";

    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public WebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcast(FermenterStatusDto dto) {
        messagingTemplate.convertAndSend(TOPIC, dto);
        log.debug("WebSocket broadcast sent: deviceName={}", dto.getDeviceName());
    }
}
