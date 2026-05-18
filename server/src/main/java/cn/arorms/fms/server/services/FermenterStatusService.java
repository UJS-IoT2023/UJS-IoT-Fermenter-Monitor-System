package cn.arorms.fms.server.services;

import cn.arorms.fms.server.dto.FermenterStatusDTO;
import cn.arorms.fms.server.entities.FermenterStatus;
import cn.arorms.fms.server.enums.ControlMode;
import cn.arorms.fms.server.repositories.FermenterStatusRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class FermenterStatusService {

    private final FermenterStatusRepository fermenterStatusRepository;
    private final ObjectMapper objectMapper;
    private final RedisService redisService;

    @Autowired
    public FermenterStatusService(FermenterStatusRepository fermenterStatusRepository,
                                  ObjectMapper objectMapper,
                                  RedisService redisService) {
        this.fermenterStatusRepository = fermenterStatusRepository;
        this.objectMapper = objectMapper;
        this.redisService = redisService;
    }

    public Page<FermenterStatus> getAllStatus(Pageable pageable) {
        return fermenterStatusRepository.findAll(pageable);
    }

    public void save(FermenterStatus status) {
        fermenterStatusRepository.save(status);
    }

    public FermenterStatusDTO processAndSaveToRedis(String jsonString) throws Exception {
        JsonNode rootNode = objectMapper.readTree(jsonString);

        String deviceName = rootNode.path("deviceName").asText();
        JsonNode items = rootNode.path("items");

        if (items.isMissingNode()) {
            log.warn("items field missing from message: deviceName={}", deviceName);
            return null;
        }

        FermenterStatusDTO dto = FermenterStatusDTO.builder()
                .deviceName(deviceName)
                .temperature(getFloat(items, "temperature"))
                .phValue(getFloat(items, "phValue"))
                .dissolvedOxygen(getFloat(items, "dissolvedOxygen"))
                .foamLevel(getFloat(items, "foamLevel"))
                .addAcid(getFloat(items, "addAcid"))
                .addAlkali(getFloat(items, "addAlkali"))
                .cooling(getFloat(items, "cooling"))
                .heating(getFloat(items, "heating"))
                .stirring(getFloat(items, "stirring"))
                .controlMode(items.has("controlMode")
                        ? ControlMode.fromCode(items.get("controlMode").get("value").asInt())
                        : null)
                .timestamp(Instant.now())
                .build();

        long gmtCreate = rootNode.path("gmtCreate").asLong(0);
        if (gmtCreate > 0) {
            dto.setTimestamp(Instant.ofEpochMilli(gmtCreate));
        }

        redisService.saveStatus(dto);
        log.info("Data saved to Redis: deviceName={}", deviceName);
        return dto;
    }

    private float getFloat(JsonNode items, String field) {
        if (items.has(field)) {
            return (float) items.get(field).get("value").asDouble();
        }
        return 0f;
    }

    public List<FermenterStatusDTO> getLatestAllDevices() {
        return redisService.getLatestAllDevices();
    }

    public List<FermenterStatusDTO> getRealtimeData(String deviceName, long fromTimestamp, long toTimestamp) {
        return redisService.getRealtimeData(deviceName, fromTimestamp, toTimestamp);
    }
}
