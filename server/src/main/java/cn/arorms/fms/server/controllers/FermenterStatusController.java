package cn.arorms.fms.server.controllers;

import cn.arorms.fms.server.entities.FermenterStatus;
import cn.arorms.fms.server.services.FermenterStatusService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fermenter-status")
public class FermenterStatusController {
    private final FermenterStatusService fermenterStatusService;
    public FermenterStatusController(FermenterStatusService fermenterStatusService) {
        this.fermenterStatusService = fermenterStatusService;
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
}
