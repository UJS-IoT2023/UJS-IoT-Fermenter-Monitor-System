package cn.arorms.fms.server.dto;

import cn.arorms.fms.server.enums.ControlMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FermenterStatusDTO implements Serializable {
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
