package cn.arorms.fms.server.amqp;

import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.qpid.jms.JmsConnectionListener;
import org.apache.qpid.jms.message.JmsInboundMessageDispatch;
import org.springframework.stereotype.Component;

import java.net.URI;

@Slf4j
@Component
public class AmqpConnectionListener implements JmsConnectionListener {

    @Override
    public void onConnectionEstablished(URI remoteURI) {
        log.info("AMQP connection established: {}", remoteURI);
    }

    @Override
    public void onConnectionFailure(Throwable error) {
        log.error("AMQP connection failed", error);
    }

    @Override
    public void onConnectionInterrupted(URI remoteURI) {
        log.warn("AMQP connection interrupted: {}", remoteURI);
    }

    @Override
    public void onConnectionRestored(URI remoteURI) {
        log.info("AMQP connection restored: {}", remoteURI);
    }

    @Override
    public void onInboundMessage(JmsInboundMessageDispatch envelope) {
    }

    @Override
    public void onSessionClosed(Session session, Throwable cause) {
        log.error("AMQP session closed", cause);
    }

    @Override
    public void onConsumerClosed(MessageConsumer consumer, Throwable cause) {
        log.error("AMQP consumer closed", cause);
    }

    @Override
    public void onProducerClosed(MessageProducer producer, Throwable cause) {
        log.error("AMQP producer closed", cause);
    }
}
