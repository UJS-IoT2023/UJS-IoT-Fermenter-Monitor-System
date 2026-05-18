package cn.arorms.fms.server.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "aliyun.iot.amqp")
public class AliyunAmqpProperties {
    private String accessKey;
    private String accessSecret;
    private String consumerGroupId;
    private String uid;
    private String iotInstanceId;
    private String region;
}
