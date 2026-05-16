package cn.arorms.fms.server.services;

import cn.arorms.fms.server.entities.FermenterStatus;
import cn.arorms.fms.server.repositories.FermenterStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class FermenterStatusService {

    private final FermenterStatusRepository fermenterStatusRepository;

    public FermenterStatusService(FermenterStatusRepository fermenterStatusRepository) {
        this.fermenterStatusRepository = fermenterStatusRepository;
    }

    public Page<FermenterStatus> getAllStatus(Pageable pageable) {
        return fermenterStatusRepository.findAll(pageable);
    }
}
