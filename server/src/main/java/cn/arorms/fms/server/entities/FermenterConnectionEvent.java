package cn.arorms.fms.server.entities;

import cn.arorms.fms.server.enums.ConnectionEventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "fermenter_connection_event")
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class FermenterConnectionEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceName;

    @Enumerated(EnumType.STRING)
    private ConnectionEventType eventType;

    private Instant eventTime;

    private String iotId;

    private String clientIp;

    private String productKey;
}