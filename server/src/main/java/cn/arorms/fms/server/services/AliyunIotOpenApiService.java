package cn.arorms.fms.server.services;

import cn.arorms.fms.server.properties.AliyunProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Service
public class AliyunIotOpenApiService {

    private static final String VERSION = "2018-01-20";
    private static final String FORMAT = "JSON";
    private static final String SIGNATURE_METHOD = "HMAC-SHA1";
    private static final String SIGNATURE_VERSION = "1.0";

    private final AliyunProperties properties;
    private final RestTemplate restTemplate;

    @Autowired
    public AliyunIotOpenApiService(AliyunProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    public void setDeviceProperty(String iotId, Map<String, Object> properties) {
        String region = this.properties.getOpenapi().getRegion();
        String endpoint = "https://iot.cn-" + region + ".aliyuncs.com";

        Map<String, String> params = new TreeMap<>();
        params.put("Action", "SetDeviceProperty");
        params.put("Format", FORMAT);
        params.put("Version", VERSION);
        params.put("AccessKey", this.properties.getOpenapi().getAccessKey());
        params.put("SignatureVersion", SIGNATURE_VERSION);
        params.put("SignatureMethod", SIGNATURE_METHOD);
        params.put("Timestamp", getTimestamp());
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("IotInstanceId", this.properties.getOpenapi().getIotInstanceId());
        params.put("DeviceName", iotId);
        params.put("Items", buildItemsParam(properties));

        String signature = computeSignature(params, this.properties.getOpenapi().getAccessSecret());
        params.put("Signature", signature);

        String url = endpoint + "/?" + buildQueryString(params);
        log.info("Calling Aliyun IoT OpenAPI SetDeviceProperty for device: {}, properties: {}", iotId, properties);
        log.debug("Full URL: {}", url);

        try {
            restTemplate.getForObject(URI.create(url), String.class);
            log.info("Successfully set device property for: {}", iotId);
        } catch (Exception e) {
            log.error("Failed to set device property for {}: {}", iotId, e.getMessage());
            throw new RuntimeException("Failed to call Aliyun IoT OpenAPI", e);
        }
    }

    private String buildItemsParam(Map<String, Object> props) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object val = entry.getValue();
            if (val instanceof Number) {
                sb.append(val);
            } else {
                sb.append("\"").append(val).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String getTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    private String computeSignature(Map<String, String> params, String accessSecret) {
        String stringToSign = "GET&%2F&" + sortAndEncodeParams(params);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec((accessSecret + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeBase64String(signData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute signature", e);
        }
    }

    private String sortAndEncodeParams(Map<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append("&");
            sb.append(urlEncode(keys.get(i))).append("=").append(urlEncode(params.get(keys.get(i))));
        }
        return sb.toString();
    }

    private String buildQueryString(Map<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if (sb.length() > 0) sb.append("&");
            sb.append(urlEncode(key)).append("=").append(urlEncode(params.get(key)));
        }
        return sb.toString();
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
