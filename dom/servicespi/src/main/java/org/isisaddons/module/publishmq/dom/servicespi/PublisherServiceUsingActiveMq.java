package org.isisaddons.module.publishmq.dom.servicespi;

import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.isis.applib.ApplicationException;
import org.apache.isis.applib.annotation.DomainService;
import org.apache.isis.applib.annotation.NatureOfService;
import org.apache.isis.applib.services.publish.PublisherService;
import org.apache.isis.schema.aim.v2.ActionInvocationMementoDto;
import org.apache.isis.schema.utils.ActionInvocationMementoDtoUtils;

import org.isisaddons.module.publishmq.PublishMqModule;

@DomainService(
        nature = NatureOfService.DOMAIN
)
public class PublisherServiceUsingActiveMq implements PublisherService {

    private final static Logger LOG = LoggerFactory.getLogger(PublisherServiceUsingActiveMq.class);

    private static final String ROOT = PublishMqModule.class.getPackage().getName() + ".";

    public static final String KEY_VM_TRANSPORT_URL = "isis.services." + PublisherServiceUsingActiveMq.class.getSimpleName() + ".vmTransportUri";
    public static final String KEY_VM_TRANSPORT_URL_DEFAULT = "vm://broker";

    public static final String KEY_ACTION_INVOCATIONS_QUEUE = "isis.services." + PublisherServiceUsingActiveMq.class.getSimpleName() + ".actionInvocationsQueue";
    public static final String KEY_ACTION_INVOCATIONS_QUEUE_DEFAULT = "actionInvocationsQueue";

    private ConnectionFactory jmsConnectionFactory;
    private Connection jmsConnection;

    private static boolean transacted = true;

    private String vmTransportUrl;
    String actionInvocationsQueueName;


    @PostConstruct
    public void init(Map<String,String> properties) {

        vmTransportUrl = properties.getOrDefault(KEY_VM_TRANSPORT_URL, KEY_VM_TRANSPORT_URL_DEFAULT);
        actionInvocationsQueueName = properties.getOrDefault(KEY_ACTION_INVOCATIONS_QUEUE, KEY_ACTION_INVOCATIONS_QUEUE_DEFAULT);

        jmsConnectionFactory = new ActiveMQConnectionFactory(vmTransportUrl);

        try {
            jmsConnection = jmsConnectionFactory.createConnection();
        } catch (JMSException e) {
            LOG.error("Unable to create connection", e);
            throw new RuntimeException(e);
        }

        try {
            jmsConnection.start();
        } catch (JMSException e) {
            LOG.error("Unable to start connection", e);
            closeSafely(jmsConnection);
            jmsConnection = null;
        }
    }

    @PreDestroy
    public void shutdown() {
        closeSafely(jmsConnection);
    }


    @Override
    public void publish(final ActionInvocationMementoDto aimDto) {
        send(aimDto);
    }

    @Override
    public void republish(final ActionInvocationMementoDto aimDto) {
        send(aimDto);
    }


    private void send(final ActionInvocationMementoDto aimDto) {
        Session session = null;
        try {

            final String aimXml = ActionInvocationMementoDtoUtils.toXml(aimDto);
            session = jmsConnection.createSession(transacted, Session.SESSION_TRANSACTED);
            TextMessage message = session.createTextMessage(aimXml);

            message.setJMSMessageID( aimDto.getInvocation().getId());
            message.setJMSType(aimDto.getInvocation().getAction().getActionIdentifier());

            LOG.info("Sending JMS message, id:" + aimDto.getInvocation().getId() + "; type:" + message.getJMSType());
            final Queue queue = session.createQueue(actionInvocationsQueueName);
            MessageProducer producer = session.createProducer(queue);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            producer.send(message);

            session.commit();

        } catch (JMSException e) {
            rollback(session);
            throw new ApplicationException("Failed to publish message", e);
        } finally {
            if(session != null) {
                closeSafely(session);
            }
        }
    }


    private static void rollback(final Session session) {
        try {
            if (session != null) {
                session.rollback();
            }
        } catch (JMSException ex) {
            // ignore
        }
    }


    ///////////////////////////////////////////////////
    // Helper
    ///////////////////////////////////////////////////

    private static void closeSafely(Connection connection) {
        if(connection != null) {
            try {
                connection.close();
            } catch (JMSException e) {
                //ignore
            }
        }
    }

    private static void closeSafely(Session session) {
        try {
            session.close();
        } catch (JMSException e) {
            // ignore
        }
    }

    private static void stopSafely(final BrokerService broker) {
        if(broker==null) {
            return;
        }
        try {
            broker.stop();
        } catch (Exception ignore) {
        }
    }


}