package cn.arorms.fms.server.services;

import cn.arorms.fms.server.dto.FermenterConnectionStatusDto;
import cn.arorms.fms.server.entities.FermenterConnectionEvent;
import cn.arorms.fms.server.enums.ConnectionEventType;
import cn.arorms.fms.server.mappers.FermenterConnectionStatusMapper;
import cn.arorms.fms.server.repositories.FermenterConnectionRedisRepository;
import cn.arorms.fms.server.repositories.FermenterConnectionEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FermenterConnectionService {

    private final FermenterConnectionEventRepository repository;
    private final FermenterConnectionRedisRepository fermenterConnectionRedisRepository;
    private final FermenterConnectionStatusMapper mapper;
    private final ObjectMapper objectMapper;

    @Autowired
    public FermenterConnectionService(FermenterConnectionEventRepository repository,
                                      FermenterConnectionRedisRepository fermenterConnectionRedisRepository,
                                      FermenterConnectionStatusMapper mapper,
                                      ObjectMapper objectMapper) {
        this.repository = repository;
        this.fermenterConnectionRedisRepository = fermenterConnectionRedisRepository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public Page<FermenterConnectionStatusDto> getFermenterConnectionEventByDevice(String deviceName, Pageable pageable) {
        return repository.findByDeviceName(deviceName, pageable).map(mapper::toDto);
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
        fermenterConnectionRedisRepository.saveDeviceOnlineStatus(deviceName, isOnline, timeStr,
                event.getIotId(), event.getClientIp());

        return event;
    }

    public FermenterConnectionStatusDto getDeviceConnectionStatus(String deviceName) {
        Map<Object, Object> entries = fermenterConnectionRedisRepository.getDeviceConnectionStatus(deviceName);
        return FermenterConnectionStatusDto.builder()
                .deviceName(deviceName)
                .online(entries.get("online").equals("true"))
                .lastTime((String) entries.get("lastTime"))
                .iotId((String) entries.get("iotId"))
                .clientIp((String) entries.get("clientIp"))
                .build();

    }

    public List<FermenterConnectionStatusDto> getAllOnlineDeviceDetails() {
        return fermenterConnectionRedisRepository.getAllOnlineDeviceDetails();
    }
}
