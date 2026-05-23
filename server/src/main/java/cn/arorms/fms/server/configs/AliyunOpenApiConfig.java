package cn.arorms.fms.server.configs;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties
public class AliyunOpenApiConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
