package cn.arorms.fms.server.repositories;

import cn.arorms.fms.server.dto.FermenterStatusDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParseException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Slf4j
@Repository
public class FermenterStatusRedisRepository {

    private static final String KEY_PREFIX = "fermenter:status:";
    private static final long TWENTY_MINUTES_MS = 20 * 60 * 1000L;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public FermenterStatusRedisRepository(RedisTemplate<String, String> redisTemplate,
                                          ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private String key(String deviceName) {
        return KEY_PREFIX + deviceName;
    }

    public void save(FermenterStatusDto dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            long timestamp = dto.getTimestamp() != null
                    ? dto.getTimestamp().toEpochMilli()
                    : System.currentTimeMillis();
            String k = key(dto.getDeviceName());
            redisTemplate.opsForZSet().add(k, json, timestamp);
            redisTemplate.opsForZSet().removeRangeByScore(k, 0, timestamp - TWENTY_MINUTES_MS);
        } catch (JsonParseException e) {
            log.error("Redis serialization failed: deviceName={}", dto.getDeviceName(), e);
        }
    }

    public FermenterStatusDto getLatest(String deviceName) {
        String k = key(deviceName);
        Set<String> result = redisTemplate.opsForZSet().reverseRange(k, 0, 0);
        if (result == null || result.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(result.iterator().next(), FermenterStatusDto.class);
        } catch (JsonParseException e) {
            log.error("Redis deserialization failed: deviceName={}", deviceName, e);
            return null;
        }
    }

//    public List<FermenterStatusDto> getLatestAllDevices() {
//        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
//        if (keys == null || keys.isEmpty()) {
//            return Collections.emptyList();
//        }
//        List<FermenterStatusDto> result = new ArrayList<>();
//        for (String k : keys) {
//            String deviceName = k.substring(KEY_PREFIX.length());
//            FermenterStatusDto latest = getLatest(deviceName);
//            if (latest != null) {
//                result.add(latest);
//            }
//        }
//        return result;
//    }

    public List<FermenterStatusDto> getRealtimeData(String deviceName, long fromTimestamp, long toTimestamp) {
        String k = key(deviceName);
        Set<String> raw = redisTemplate.opsForZSet().rangeByScore(k, fromTimestamp, toTimestamp);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<FermenterStatusDto> result = new ArrayList<>();
        for (String json : raw) {
            try {
                result.add(objectMapper.readValue(json, FermenterStatusDto.class));
            } catch (JsonParseException e) {
                log.error("Redis deserialization failed", e);
            }
        }
        return result;
    }

    public List<FermenterStatusDto> getLast20Minutes(String deviceName) {
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