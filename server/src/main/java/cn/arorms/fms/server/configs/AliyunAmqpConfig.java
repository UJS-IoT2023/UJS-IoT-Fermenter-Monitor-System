package cn.arorms.fms.server.configs;

import cn.arorms.fms.server.properties.AliyunAmqpProperties;
import cn.arorms.fms.server.amqp.AmqpConnectionListener;
import cn.arorms.fms.server.amqp.AmqpMessageListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.jms.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.qpid.jms.JmsConnection;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;

@Slf4j
@Configuration
@EnableConfigurationProperties(AliyunAmqpProperties.class)
public class AliyunAmqpConfig {

    private final AliyunAmqpProperties properties;
    private final AmqpMessageListener messageListener;
    private final AmqpConnectionListener connectionListener;

    private Connection connection;
    private Session session;
    private MessageConsumer consumer;

    public AliyunAmqpConfig(AliyunAmqpProperties properties,
                            AmqpMessageListener messageListener,
                            AmqpConnectionListener connectionListener) {
        this.properties = properties;
        this.messageListener = messageListener;
        this.connectionListener = connectionListener;
    }

    @PostConstruct
    public void init() throws Exception {
        long timestamp = System.currentTimeMillis();
        String clientId = java.util.UUID.randomUUID().toString();

        String userName = clientId + "|authMode=aksign"
                + ",signMethod=hmacsha1"
                + ",timestamp=" + timestamp
                + ",authId=" + properties.getAccessKey()
                + ",iotInstanceId=" + properties.getIotInstanceId()
                + ",consumerGroupId=" + properties.getConsumerGroupId()
                + "|";

        String signContent = "authId=" + properties.getAccessKey() + "&timestamp=" + timestamp;
        String password = doSign(signContent, properties.getAccessSecret());

        String connectionUrl = "amqps://" + properties.getUid()
                + ".iot-amqp." + properties.getRegion()
                + ".aliyuncs.com:5671?amqp.idleTimeout=80000";

        Hashtable<String, String> hashtable = new Hashtable<>();
        hashtable.put("connectionfactory.SBCF", connectionUrl);
        hashtable.put("queue.QUEUE", "receive");
        hashtable.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
        Context context = new InitialContext(hashtable);

        ConnectionFactory cf = (ConnectionFactory) context.lookup("SBCF");
        connection = cf.createConnection(userName, password);

        ((JmsConnection) connection).addConnectionListener(connectionListener);
        connection.start();
        log.info("Successfully connected to Aliyun AMQP server");

        session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Destination queue = (Destination) context.lookup("QUEUE");
        consumer = session.createConsumer(queue);
        consumer.setMessageListener(messageListener);
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (consumer != null) consumer.close();
        } catch (JMSException e) {
            log.warn("Error closing AMQP consumer", e);
        }
        try {
            if (session != null) session.close();
        } catch (JMSException e) {
            log.warn("Error closing AMQP session", e);
        }
        try {
            if (connection != null) connection.close();
        } catch (JMSException e) {
            log.warn("Error closing AMQP connection", e);
        }
        log.info("AMQP connection closed");
    }

    private String doSign(String content, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] signData = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeBase64String(signData);
    }
}
