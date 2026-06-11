package com.evanp.f1.api.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.evanp.f1.api.TestApplication;
import com.evanp.f1.api.TestInfrastructureConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = TestApplication.class)
@Import(TestInfrastructureConfiguration.class)
@ActiveProfiles("test")
class WebSocketConfigTest {

    @Autowired
    WebSocketConfig webSocketConfig;

    @Autowired
    SimpMessagingTemplate messagingTemplate;

    @Test
    void contextLoadsWebSocketBeans() {
        assertThat(webSocketConfig).isNotNull();
        assertThat(messagingTemplate).isNotNull();
    }
}
