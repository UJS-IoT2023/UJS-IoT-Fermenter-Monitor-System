package cn.arorms.fms.server.controllers;

import cn.arorms.fms.server.dto.FermenterControlCommand;
import cn.arorms.fms.server.repositories.FermenterConnectionRedisRepository;
import cn.arorms.fms.server.services.AliyunIotOpenApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/fermenter-control")
public class FermenterControlController {

    private final AliyunIotOpenApiService openApiService;
    private final FermenterConnectionRedisRepository connectionRepo;

    @Autowired
    public FermenterControlController(AliyunIotOpenApiService openApiService,
                                       FermenterConnectionRedisRepository connectionRepo) {
        this.openApiService = openApiService;
        this.connectionRepo = connectionRepo;
    }

    @PostMapping("/command")
    public ResponseEntity<Void> sendCommand(@RequestBody FermenterControlCommand command) {
        String iotId = connectionRepo.getIotIdByDeviceName(command.getDeviceName());
        if (iotId == null || iotId.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> props = new HashMap<>();
        if (command.getAddAcid() != null) props.put("addAcid", command.getAddAcid());
        if (command.getAddAlkali() != null) props.put("addAlkali", command.getAddAlkali());
        if (command.getCooling() != null) props.put("cooling", command.getCooling());
        if (command.getHeating() != null) props.put("heating", command.getHeating());
        if (command.getStirring() != null) props.put("stirring", command.getStirring());
        if (command.getControlMode() != null) props.put("controlMode", command.getControlMode());

        openApiService.setDeviceProperty(iotId, props);
        return ResponseEntity.ok().build();
    }
}
