package cn.arorms.fms.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FermenterControlCommand {
    private String deviceName;
    private Integer addAcid;
    private Integer addAlkali;
    private Integer cooling;
    private Integer heating;
    private Integer stirring;
    private Integer controlMode;
}
