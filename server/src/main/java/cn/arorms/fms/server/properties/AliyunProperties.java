package cn.arorms.fms.server.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "aliyun.iot")
public class AliyunProperties {
    private Amqp amqp = new Amqp();
    private Openapi openapi = new Openapi();

    @Getter
    @Setter
    public static class Amqp {
        private String accessKey;
        private String accessSecret;
        private String consumerGroupId;
        private String uid;
        private String iotInstanceId;
        private String region;
        private int idleTimeout;
    }

    @Getter
    @Setter
    public static class Openapi {
        private String region;
        private String accessKey;
        private String accessSecret;
        private String iotInstanceId;
    }
}
