package cn.arorms.fms.server.repositories;

import cn.arorms.fms.server.entities.FermenterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FermenterStatusRepository extends JpaRepository<FermenterStatus, Long> {
    Page<FermenterStatus> findByDeviceName(String deviceName, Pageable pageable);
}
