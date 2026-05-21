package cn.arorms.fms.server.controllers;

import cn.arorms.fms.server.dto.FermenterStatusDto;
import cn.arorms.fms.server.entities.FermenterStatus;
import cn.arorms.fms.server.repositories.FermenterStatusRepository;
import cn.arorms.fms.server.services.FermenterStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/fermenter-status")
public class FermenterStatusController {
    private final FermenterStatusService fermenterStatusService;
    private final FermenterStatusRepository repository;

    @Autowired
    public FermenterStatusController(FermenterStatusService fermenterStatusService,
                                      FermenterStatusRepository repository) {
        this.fermenterStatusService = fermenterStatusService;
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<Page<FermenterStatus>> getAllFermenterStatus(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Sort sort = Sort.by("timestamp").descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(fermenterStatusService.getAllStatus(pageable));
    }

    @GetMapping("/{device_name}/latest")
    public ResponseEntity<FermenterStatusDto> getFermenterLatestStatus(@PathVariable("device_name") String deviceName) {
        return ResponseEntity.ok(fermenterStatusService.getFermenterLatestStatusByDeviceName(deviceName));
    }

    @GetMapping("/realtime")
    public ResponseEntity<List<FermenterStatusDto>> getRealtime(
            @RequestParam(required = false) String deviceName) {
        long now = System.currentTimeMillis();
        long oneHourAgo = now - 60 * 60 * 1000L;
        if (deviceName != null && !deviceName.isBlank()) {
            return ResponseEntity.ok(fermenterStatusService.getRealtimeData(deviceName, oneHourAgo, now));
        }
        return ResponseEntity.ok(fermenterStatusService.getLatestAllDevices());
    }

    @GetMapping("/history")
    public ResponseEntity<Page<FermenterStatus>> getHistory(
            @RequestParam(required = false) String deviceName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<FermenterStatus> result;
        if (deviceName != null && !deviceName.isBlank()) {
            result = repository.findByDeviceName(deviceName, pageable);
        } else {
            result = repository.findAll(pageable);
        }
        return ResponseEntity.ok(result);
    }
}
