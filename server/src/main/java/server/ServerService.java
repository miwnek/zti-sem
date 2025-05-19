package server;

import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;
import java.time.LocalTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.activemq.command.ActiveMQTopic;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import jakarta.jms.Destination;

@Component
public class ServerService {

    private final JmsTemplate jmsTemplate; // jmsTemplate.setPubSubDomain(true); -> topics as default
    private final ActiveMQTopic evensTopic;
    private final ActiveMQTopic oddsTopic;
    private final Set<String> registeredIds;

    public ServerService(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
        this.evensTopic = new ActiveMQTopic("even_ids");
        this.oddsTopic = new ActiveMQTopic("odd_ids");
        this.registeredIds = ConcurrentHashMap.newKeySet();
    }

    @JmsListener(destination = "client.ids")
    public void onRegisterRequest(TextMessage message) throws JMSException{
        String clientId = message.getText();
        Destination replyDestination = message.getJMSReplyTo();

        String response;
        synchronized (registeredIds) {
            if (registeredIds.contains(clientId)) {
                response = "TAKEN";
                System.out.println("ID already taken: " + clientId);
            } else {
                registeredIds.add(clientId);
                response = "OK";
                System.out.println("Registered new ID: " + clientId);
            }
        }

        // Send response
        jmsTemplate.send(replyDestination, session -> session.createTextMessage(response));
    }

    @JmsListener(destination = "client.dereg")
    public void onDeregisterRequest(TextMessage message) throws JMSException {
        String clientId = message.getText();
        if (registeredIds.remove(clientId)) {
            System.out.println("Deregistered ID: " + clientId);
        } else {
            System.out.println("Deregister request for unknown ID: " + clientId);
        }
    }

    @Scheduled(fixedRate = 8000)
    public void sendToEvenTopic() {
        String msg = "Message to even IDs @ " + LocalTime.now();
        jmsTemplate.convertAndSend(this.evensTopic, msg);
        System.out.println("Sent to even_ids topic: " + msg);
    }

    @Scheduled(initialDelay = 4000, fixedRate = 8000)
    public void sendToOddTopic() {
        String msg = "Message to odd IDs @ " + LocalTime.now();
        jmsTemplate.convertAndSend(this.oddsTopic, msg);
        System.out.println("Sent to odd_ids topic: " + msg);
    }
}


