package ir.ut.se.tinyme.messaging;

import ir.ut.se.tinyme.messaging.event.Event;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;
import com.fasterxml.jackson.core.JsonProcessingException;

@Component
public class EventPublisher {
    private final Logger log = Logger.getLogger(this.getClass().getName());
    private final JmsTemplate jmsTemplate;
    @Value("${responseQueue}")
    private String responseQueue;

    public EventPublisher(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public void publish(Event event) {
        log.info("Published : " + event);
        jmsTemplate.convertAndSend(responseQueue, event);
    }

    public void publishMany(List<Event> events) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
          String eventsJson = objectMapper.writeValueAsString(events);
          log.info("Published : " + eventsJson);
          jmsTemplate.convertAndSend(responseQueue, eventsJson);
        } catch (JsonProcessingException e) {
          log.log(Level.SEVERE, "Failed to publish events", e);
        }
    }
}
