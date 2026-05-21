package cn.arorms.fms.server.repositories;

import cn.arorms.fms.server.dto.FermenterConnectionStatusDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Slf4j
@Repository
public class FermenterConnectionRedisRepository {

    private static final String KEY_PREFIX = "fermenter:device:";

    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public FermenterConnectionRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveDeviceOnlineStatus(String deviceName, boolean isOnline, String lastTime, String iotId, String clientIp) {
        String key = KEY_PREFIX + deviceName;
        Map<String, String> map = new HashMap<>();
        map.put("isOnline", String.valueOf(isOnline));
        map.put("lastTime", lastTime != null ? lastTime : "");
        map.put("iotId", iotId != null ? iotId : "");
        map.put("clientIp", clientIp != null ? clientIp : "");
        redisTemplate.opsForHash().putAll(key, map);
    }

    public Map<Object, Object> getDeviceConnectionStatus(String deviceName) {
        String key = KEY_PREFIX + deviceName;
        return redisTemplate.opsForHash().entries(key);
    }
//
//    public Set<String> getAllOnlineDeviceNames() {
//        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
//        if (keys == null) {
//            return Collections.emptySet();
//        }
//        Set<String> deviceNames = new HashSet<>();
//        for (String k : keys) {
//            deviceNames.add(k.substring(KEY_PREFIX.length()));
//        }
//        return deviceNames;
//    }

    public boolean isDeviceOnline(String deviceName) {
        String key = KEY_PREFIX + deviceName;
        Object isOnline = redisTemplate.opsForHash().get(key, "isOnline");
        return "true".equals(isOnline);
    }

    public List<FermenterConnectionStatusDto> getAllOnlineDeviceDetails() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<FermenterConnectionStatusDto> result = new ArrayList<>();
        for (String k : keys) {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(k);
            if (entries.isEmpty()) {
                continue;
            }
            String deviceName = k.substring(KEY_PREFIX.length());
            FermenterConnectionStatusDto dto = FermenterConnectionStatusDto.builder()
                    .deviceName(deviceName)
                    .isOnline("true".equals(entries.get("isOnline")))
                    .lastTime((String) entries.get("lastTime"))
                    .iotId((String) entries.get("iotId"))
                    .clientIp((String) entries.get("clientIp"))
                    .build();
            result.add(dto);
        }
        return result;
    }
}
