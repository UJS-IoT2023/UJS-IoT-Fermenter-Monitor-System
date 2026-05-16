package cn.arorms.fms.server.entities;

import cn.arorms.fms.server.enums.ControlMode;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "fermenter_status")
public class FermenterStatus {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceName;

    private float temperature;
    private float phValue;
    private float dissolvedOxygen;
    private float foamLevel;

    private float addAcid;
    private float addAlkali;
    private float cooling;
    private float heating;
    private float stirring;

    private ControlMode controlMode;

    private Instant timestamp;
}
