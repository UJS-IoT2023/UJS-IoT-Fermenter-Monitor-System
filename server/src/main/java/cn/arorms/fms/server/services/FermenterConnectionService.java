package cn.arorms.fms.server.services;

import cn.arorms.fms.server.entities.FermenterConnectionEvent;
import cn.arorms.fms.server.enums.ConnectionEventType;
import cn.arorms.fms.server.repositories.FermenterConnectionEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Slf4j
@Service
public class FermenterConnectionService {

    private final FermenterConnectionEventRepository repository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    @Autowired
    public FermenterConnectionService(FermenterConnectionEventRepository repository,
                                      RedisService redisService,
                                      ObjectMapper objectMapper) {
        this.repository = repository;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
    }

    public FermenterConnectionEvent processAndSave(String jsonString) throws Exception {
        JsonNode rootNode = objectMapper.readTree(jsonString);

        String deviceName = rootNode.path("deviceName").asText();
        String status = rootNode.path("status").asText();
        ConnectionEventType eventType = ConnectionEventType.fromString(status);

        FermenterConnectionEvent event = new FermenterConnectionEvent();
        event.setDeviceName(deviceName);
        event.setEventType(eventType);
        event.setIotId(rootNode.path("iotId").asText(null));
        event.setClientIp(rootNode.path("clientIp").asText(null));
        event.setProductKey(rootNode.path("productKey").asText(null));

        String timeStr = rootNode.path("lastTime").asText(null);
        if (timeStr != null && !timeStr.isEmpty()) {
            event.setEventTime(Instant.now());
        } else {
            event.setEventTime(Instant.now());
        }

        repository.save(event);
        log.info("Connection event saved: deviceName={}, eventType={}", deviceName, eventType);

        boolean isOnline = eventType == ConnectionEventType.ONLINE;
        redisService.saveDeviceOnlineStatus(deviceName, isOnline, timeStr,
                event.getIotId(), event.getClientIp());

        return event;
    }
}