package ir.ut.se.tinyme.config;

import ir.ut.se.tinyme.messaging.EventPublisher;
import ir.ut.se.tinyme.messaging.RequestDispatcher;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;

@TestConfiguration
public class MockedJMSTestConfig {
    @MockBean
    EventPublisher eventPublisher;
    @MockBean
    RequestDispatcher requestDispatcher;
}
