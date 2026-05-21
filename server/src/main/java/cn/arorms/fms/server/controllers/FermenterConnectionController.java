package cn.arorms.fms.server.controllers;

import cn.arorms.fms.server.dto.FermenterConnectionStatusDto;
import cn.arorms.fms.server.dto.FermenterStatusDto;
import cn.arorms.fms.server.services.FermenterConnectionService;
import jakarta.websocket.server.PathParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/fermenter-connection")
public class FermenterConnectionController {

    private final FermenterConnectionService fermenterConnectionService;

    @Autowired
    public FermenterConnectionController(FermenterConnectionService fermenterConnectionService) {
        this.fermenterConnectionService = fermenterConnectionService;
    }

    @GetMapping("/devices")
    public List<FermenterConnectionStatusDto> getAllFermenterDevices() {
        return fermenterConnectionService.getAllOnlineDeviceDetails();
    }

    @GetMapping("/{device_name}")
    public FermenterConnectionStatusDto getDeviceStatusByDeviceName(@PathVariable(name = "device_name") String deviceName) {
        return fermenterConnectionService.getDeviceConnectionStatus(deviceName);
    }

    @GetMapping("/{device_name}/event")
    public Page<FermenterConnectionStatusDto> getFermenterConnectionStatusByDeviceName(
            @PathVariable(name = "device_name") String deviceName,
            @PageableDefault(page = 0, size = 10, sort = "eventTime", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return fermenterConnectionService.getFermenterConnectionEventByDevice(deviceName, pageable);
    }
}
