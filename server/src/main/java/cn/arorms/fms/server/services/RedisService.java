package cn.arorms.fms.server.services;

import cn.arorms.fms.server.dto.FermenterStatusDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParseException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Slf4j
@Service
public class RedisService {

    private static final String KEY_PREFIX = "fermenter:status:";
    private static final long ONE_HOUR_MS = 60 * 60 * 1000L;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public RedisService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private String key(String deviceName) {
        return KEY_PREFIX + deviceName;
    }

    public void saveStatus(FermenterStatusDTO dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            long timestamp = dto.getTimestamp() != null
                    ? dto.getTimestamp().toEpochMilli()
                    : System.currentTimeMillis();
            String k = key(dto.getDeviceName());
            redisTemplate.opsForZSet().add(k, json, timestamp);
            redisTemplate.opsForZSet().removeRangeByScore(k, 0, timestamp - ONE_HOUR_MS);
        } catch (JsonParseException e) {
            log.error("Redis serialization failed: deviceName={}", dto.getDeviceName(), e);
        }
    }

    public FermenterStatusDTO getLatest(String deviceName) {
        String k = key(deviceName);
        Set<String> result = redisTemplate.opsForZSet().reverseRange(k, 0, 0);
        if (result == null || result.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(result.iterator().next(), FermenterStatusDTO.class);
        } catch (JsonParseException e) {
            log.error("Redis deserialization failed: deviceName={}", deviceName, e);
            return null;
        }
    }

    public List<FermenterStatusDTO> getLatestAllDevices() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<FermenterStatusDTO> result = new ArrayList<>();
        for (String k : keys) {
            String deviceName = k.substring(KEY_PREFIX.length());
            FermenterStatusDTO latest = getLatest(deviceName);
            if (latest != null) {
                result.add(latest);
            }
        }
        return result;
    }

    public List<FermenterStatusDTO> getRealtimeData(String deviceName, long fromTimestamp, long toTimestamp) {
        String k = key(deviceName);
        Set<String> raw = redisTemplate.opsForZSet().rangeByScore(k, fromTimestamp, toTimestamp);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<FermenterStatusDTO> result = new ArrayList<>();
        for (String json : raw) {
            try {
                result.add(objectMapper.readValue(json, FermenterStatusDTO.class));
            } catch (JsonParseException e) {
                log.error("Redis deserialization failed", e);
            }
        }
        return result;
    }

    public List<FermenterStatusDTO> getLast20Minutes(String deviceName) {
        long now = System.currentTimeMillis();
        long twentyMinutesAgo = now - 20 * 60 * 1000L;
        return getRealtimeData(deviceName, twentyMinutesAgo, now);
    }

    public Set<String> getAllDeviceNames() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null) {
            return Collections.emptySet();
        }
        Set<String> deviceNames = new HashSet<>();
        for (String k : keys) {
            deviceNames.add(k.substring(KEY_PREFIX.length()));
        }
        return deviceNames;
    }
}
