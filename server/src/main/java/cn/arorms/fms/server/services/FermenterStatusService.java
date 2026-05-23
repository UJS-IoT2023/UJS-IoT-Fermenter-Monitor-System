package cn.arorms.fms.server.services;

import cn.arorms.fms.server.dto.FermenterStatusDto;
import cn.arorms.fms.server.entities.FermenterStatus;
import cn.arorms.fms.server.enums.ControlMode;
import cn.arorms.fms.server.repositories.FermenterStatusRepository;
import cn.arorms.fms.server.repositories.FermenterStatusRedisRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class FermenterStatusService {

    private final FermenterStatusRepository fermenterStatusRepository;
    private final FermenterStatusRedisRepository fermenterStatusRedisRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public FermenterStatusService(FermenterStatusRepository fermenterStatusRepository,
                                  FermenterStatusRedisRepository fermenterStatusRedisRepository,
                                  ObjectMapper objectMapper) {
        this.fermenterStatusRepository = fermenterStatusRepository;
        this.fermenterStatusRedisRepository = fermenterStatusRedisRepository;
        this.objectMapper = objectMapper;
    }

    public Page<FermenterStatus> getAllStatus(Pageable pageable) {
        return fermenterStatusRepository.findAll(pageable);
    }

    public void save(FermenterStatus status) {
        fermenterStatusRepository.save(status);
    }

    public FermenterStatusDto processAndSaveToRedis(String jsonString) throws Exception {
        JsonNode rootNode = objectMapper.readTree(jsonString);

        String deviceName = rootNode.path("deviceName").asText();
        JsonNode items = rootNode.path("items");

        if (items.isMissingNode()) {
            log.warn("items field missing from message: deviceName={}", deviceName);
            return null;
        }

        FermenterStatusDto dto = FermenterStatusDto.builder()
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

        fermenterStatusRedisRepository.save(dto);
        log.info("Data saved to Redis: deviceName={}", deviceName);
        return dto;
    }

    private float getFloat(JsonNode items, String field) {
        if (items.has(field)) {
            return (float) items.get(field).get("value").asDouble();
        }
        return 0f;
    }

    public FermenterStatusDto getFermenterLatestStatusByDeviceName(String deviceName) {
        return fermenterStatusRedisRepository.getLatest(deviceName);
    }

    public List<FermenterStatusDto> getLast20Minutes(String deviceName) {
        return fermenterStatusRedisRepository.getLast20Minutes(deviceName);
    }

//    public List<FermenterStatusDto> getLatestAllDevices() {
//        return fermenterStatusRedisRepository.getLatestAllDevices();
//    }

    public List<FermenterStatusDto> getRealtimeData(String deviceName, long fromTimestamp, long toTimestamp) {
        return fermenterStatusRedisRepository.getRealtimeData(deviceName, fromTimestamp, toTimestamp);
    }

    @Scheduled(cron = "0 30 * * * *")
    public void downsample() {
        for (String deviceName : fermenterStatusRedisRepository.getAllDeviceNames()) {
            FermenterStatusDto latest = fermenterStatusRedisRepository.getLatest(deviceName);
            if (latest == null) {
                continue;
            }

            FermenterStatus entity = new FermenterStatus();
            entity.setDeviceName(latest.getDeviceName());
            entity.setTemperature(latest.getTemperature());
            entity.setPhValue(latest.getPhValue());
            entity.setDissolvedOxygen(latest.getDissolvedOxygen());
            entity.setFoamLevel(latest.getFoamLevel());
            entity.setAddAcid(latest.getAddAcid());
            entity.setAddAlkali(latest.getAddAlkali());
            entity.setCooling(latest.getCooling());
            entity.setHeating(latest.getHeating());
            entity.setStirring(latest.getStirring());
            entity.setControlMode(latest.getControlMode());
            entity.setTimestamp(Instant.now());

            fermenterStatusRepository.save(entity);
            log.info("Downsample saved to PostgreSQL: deviceName={}", deviceName);
        }
    }
}