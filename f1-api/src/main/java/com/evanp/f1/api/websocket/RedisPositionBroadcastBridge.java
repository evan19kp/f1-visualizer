package com.evanp.f1.api.websocket;

import com.evanp.f1.core.position.NormalizedPosition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisPositionBroadcastBridge implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisPositionBroadcastBridge.class);
    private static final String CHANNEL_PATTERN = "f1:pubsub:session:*:positions";
    private static final String CHANNEL_PREFIX = "f1:pubsub:session:";
    private static final String CHANNEL_SUFFIX = ":positions";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer listenerContainer;

    public RedisPositionBroadcastBridge(
            RedisConnectionFactory connectionFactory,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.listenerContainer = new RedisMessageListenerContainer();
        this.listenerContainer.setConnectionFactory(connectionFactory);
    }

    @PostConstruct
    void startListening() {
        listenerContainer.addMessageListener(this, new PatternTopic(CHANNEL_PATTERN));
        listenerContainer.afterPropertiesSet();
        listenerContainer.start();
    }

    @PreDestroy
    void stopListening() {
        listenerContainer.stop();
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        Optional<String> sessionKey = sessionKeyFromChannel(channel);
        if (sessionKey.isEmpty()) {
            log.warn("Ignoring pub/sub message on unexpected channel: {}", channel);
            return;
        }
        try {
            List<NormalizedPosition> positions =
                    objectMapper.readValue(message.getBody(), new TypeReference<>() {});
            messagingTemplate.convertAndSend(stompDestination(sessionKey.get()), positions);
        } catch (Exception e) {
            log.warn("Failed to forward positions for session {}: {}", sessionKey.get(), e.getMessage());
        }
    }

    static Optional<String> sessionKeyFromChannel(String channel) {
        if (!channel.startsWith(CHANNEL_PREFIX) || !channel.endsWith(CHANNEL_SUFFIX)) {
            return Optional.empty();
        }
        String sessionKey = channel.substring(
                CHANNEL_PREFIX.length(), channel.length() - CHANNEL_SUFFIX.length());
        return sessionKey.isEmpty() ? Optional.empty() : Optional.of(sessionKey);
    }

    static String stompDestination(String sessionKey) {
        return "/topic/sessions/" + sessionKey + "/positions";
    }
}
