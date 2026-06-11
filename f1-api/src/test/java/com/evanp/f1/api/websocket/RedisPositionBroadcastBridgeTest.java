package com.evanp.f1.api.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.evanp.f1.core.position.NormalizedPosition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class RedisPositionBroadcastBridgeTest {

    @Mock RedisConnectionFactory connectionFactory;
    @Mock SimpMessagingTemplate messagingTemplate;

    private ObjectMapper objectMapper;
    private RedisPositionBroadcastBridge bridge;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        bridge = new RedisPositionBroadcastBridge(connectionFactory, messagingTemplate, objectMapper);
    }

    @Test
    void sessionKeyFromChannelExtractsSessionKey() {
        assertThat(RedisPositionBroadcastBridge.sessionKeyFromChannel("f1:pubsub:session:9161:positions"))
                .contains("9161");
    }

    @Test
    void forwardsPositionsToStompTopic() throws Exception {
        List<NormalizedPosition> positions = List.of(new NormalizedPosition(
                1, 9161L, Instant.parse("2024-03-02T15:00:00Z"), 0.1, 0.2, 0.3));
        byte[] body = objectMapper.writeValueAsBytes(positions);
        var message = new DefaultMessage("f1:pubsub:session:9161:positions".getBytes(), body);

        bridge.onMessage(message, "f1:pubsub:session:*:positions".getBytes());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NormalizedPosition>> payloadCaptor = ArgumentCaptor.forClass(List.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/sessions/9161/positions"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).hasSize(1);
        assertThat(payloadCaptor.getValue().getFirst().driverNumber()).isEqualTo(1);
    }
}
