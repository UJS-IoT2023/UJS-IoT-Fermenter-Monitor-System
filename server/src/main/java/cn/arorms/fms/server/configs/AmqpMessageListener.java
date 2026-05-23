package cn.arorms.fms.server.configs;

import cn.arorms.fms.server.dto.FermenterStatusDto;
import cn.arorms.fms.server.services.FermenterConnectionService;
import cn.arorms.fms.server.services.FermenterStatusService;
import cn.arorms.fms.server.services.WebSocketService;
import jakarta.jms.BytesMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class AmqpMessageListener implements MessageListener {

    private final FermenterStatusService fermenterStatusService;
    private final FermenterConnectionService fermenterConnectionService;
    private final WebSocketService webSocketService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AmqpMessageListener(FermenterStatusService fermenterStatusService,
                               FermenterConnectionService fermenterConnectionService,
                               WebSocketService webSocketService,
                               ObjectMapper objectMapper) {
        this.fermenterStatusService = fermenterStatusService;
        this.fermenterConnectionService = fermenterConnectionService;
        this.webSocketService = webSocketService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof BytesMessage bytesMessage) {
                byte[] body = new byte[(int) bytesMessage.getBodyLength()];
                bytesMessage.readBytes(body);
                String jsonString = new String(body, StandardCharsets.UTF_8);

                log.info("Received device data: {}", jsonString);

                JsonNode rootNode = objectMapper.readTree(jsonString);

                if (rootNode.has("status")) {
                    var event = fermenterConnectionService.processAndSave(jsonString);
                    log.info("Device connection event processed: deviceName={}, eventType={}",
                            event.getDeviceName(), event.getEventType());
                } else if (rootNode.has("items")) {
                    FermenterStatusDto dto = fermenterStatusService.processAndSaveToRedis(jsonString);
                    if (dto != null) {
                        webSocketService.broadcast(dto);
                    }
                } else {
                    log.warn("Unknown message format: neither status nor items field found");
                }
            }
            message.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process AMQP message", e);
        }
    }
}