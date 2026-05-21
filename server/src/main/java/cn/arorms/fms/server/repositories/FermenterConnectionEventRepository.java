package cn.arorms.fms.server.repositories;

import cn.arorms.fms.server.entities.FermenterConnectionEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FermenterConnectionEventRepository extends JpaRepository<FermenterConnectionEvent, Long> {
    Page<FermenterConnectionEvent> findByDeviceName(String deviceName, Pageable pageable);
}