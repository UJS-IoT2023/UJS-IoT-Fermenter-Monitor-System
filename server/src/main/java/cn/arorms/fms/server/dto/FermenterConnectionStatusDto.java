package cn.arorms.fms.server.dto;

import cn.arorms.fms.server.enums.ConnectionEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FermenterConnectionStatusDto {
    private Long id;
    private String deviceName;
    private ConnectionEventType eventType;
    private Instant eventTime;
    private String iotId;
    private String clientIp;
    private String productKey;
    private boolean online;
    private String lastTime;
}
