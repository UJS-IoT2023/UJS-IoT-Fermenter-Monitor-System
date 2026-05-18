package cn.arorms.fms.server.amqp;

import cn.arorms.fms.server.dto.FermenterStatusDTO;
import cn.arorms.fms.server.services.FermenterStatusService;
import cn.arorms.fms.server.services.WebSocketService;
import jakarta.jms.BytesMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class AmqpMessageListener implements MessageListener {

    private final FermenterStatusService fermenterStatusService;
    private final WebSocketService webSocketService;

    public AmqpMessageListener(FermenterStatusService fermenterStatusService,
                               WebSocketService webSocketService) {
        this.fermenterStatusService = fermenterStatusService;
        this.webSocketService = webSocketService;
    }

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof BytesMessage bytesMessage) {
                byte[] body = new byte[(int) bytesMessage.getBodyLength()];
                bytesMessage.readBytes(body);
                String jsonString = new String(body, StandardCharsets.UTF_8);

                log.info("Received device data: {}", jsonString);
                FermenterStatusDTO dto = fermenterStatusService.processAndSaveToRedis(jsonString);
                if (dto != null) {
                    webSocketService.broadcast(dto);
                }
            }
            message.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process AMQP message", e);
        }
    }
}
