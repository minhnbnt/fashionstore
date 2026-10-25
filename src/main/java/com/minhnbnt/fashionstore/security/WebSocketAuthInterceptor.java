package com.minhnbnt.fashionstore.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Xác thực kết nối WebSocket: đọc JWT từ header Authorization của frame CONNECT,
 * verify bằng JwtDecoder của resource server rồi gắn Principal theo JWT sub.
 * Từ đó mỗi phiên socket có tên = userId để server đẩy tin qua /user/queue/....
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        accessor.setUser(authenticate(accessor));

        return message;
    }

    private JwtAuthenticationToken authenticate(StompHeaderAccessor accessor) {
        var authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new MessagingException("Missing bearer token");
        }

        try {
            Jwt jwt = jwtDecoder.decode(authorization.substring("Bearer ".length()));
            return new JwtAuthenticationToken(jwt, List.of());
        } catch (JwtException e) {
            log.warn("WebSocket connect rejected: {}", e.getMessage());
            throw new MessagingException("Invalid or expired token");
        }
    }
}
