package cn.arorms.fms.server.tasks;

import cn.arorms.fms.server.dto.FermenterStatusDTO;
import cn.arorms.fms.server.entities.FermenterStatus;
import cn.arorms.fms.server.repositories.FermenterStatusRepository;
import cn.arorms.fms.server.services.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class DownsampleTask {

    private final RedisService redisService;
    private final FermenterStatusRepository repository;

    @Autowired
    public DownsampleTask(RedisService redisService, FermenterStatusRepository repository) {
        this.redisService = redisService;
        this.repository = repository;
    }

    @Scheduled(cron = "0 30 * * * *")
    public void downsample() {

        for (String deviceName : redisService.getAllDeviceNames()) {
            FermenterStatusDTO latest = redisService.getLatest(deviceName);
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

            repository.save(entity);
            log.info("Downsample saved to PostgreSQL: deviceName={}", deviceName);
        }
    }
}
