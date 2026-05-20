package cn.arorms.fms.server.repositories;

import cn.arorms.fms.server.entities.FermenterConnectionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FermenterConnectionEventRepository extends JpaRepository<FermenterConnectionEvent, Long> {
}