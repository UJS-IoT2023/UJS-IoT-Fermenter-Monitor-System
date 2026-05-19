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
    private FermenterStatusDTO applyFuzzyControl(FermenterStatusDTO dto) {
    
    
    final double TARGET_TEMP = 30.0;   // ℃
    final double TARGET_PH = 7.0;      // pH
    
    //计算误差
    double tempError = dto.getTemperature() - TARGET_TEMP;
    double phError = dto.getPhValue() - TARGET_PH;
    
    log.info("模糊控制输入 -> 温度误差={}, pH误差={}", tempError, phError);
    
    //温度模糊规则
    float heating = 0.0f;
    float cooling = 0.0f;
    
    if (tempError > 2.0) {
        cooling = 1.0f;   // 强冷却
    } else if (tempError > 0.5) {
        cooling = 0.5f;   // 弱冷却
    } else if (tempError < -2.0) {
        heating = 1.0f;   // 强加热
    } else if (tempError < -0.5) {
        heating = 0.5f;   // 弱加热
    }
    
    //pH 模糊规则
    float addAcid = 0.0f;
    float addAlkali = 0.0f;
    
    if (phError > 0.2) {
        addAcid = 1.0f;   // 偏碱 → 加酸
    } else if (phError > 0.05) {
        addAcid = 0.5f;
    } else if (phError < -0.2) {
        addAlkali = 1.0f; // 偏酸 → 加碱
    } else if (phError < -0.05) {
        addAlkali = 0.5f;
    }
    
    //写回 DTO
    dto.setHeating(heating);
    dto.setCooling(cooling);
    dto.setAddAcid(addAcid);
    dto.setAddAlkali(addAlkali);
    
    log.info("模糊控制输出 -> heating={}, cooling={}, addAcid={}, addAlkali={}",
            heating, cooling, addAcid, addAlkali);
    
    return dto;
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
        dto = applyFuzzyControl(dto);
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
