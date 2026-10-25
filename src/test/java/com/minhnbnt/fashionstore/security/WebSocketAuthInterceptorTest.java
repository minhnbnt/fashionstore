package com.minhnbnt.fashionstore.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    private final MessageChannel channel = mock(MessageChannel.class);

    @Mock
    private JwtDecoder jwtDecoder;

    @InjectMocks
    private WebSocketAuthInterceptor interceptor;

    @Test
    void connectWithValidTokenSetsPrincipalToJwtSubject() {
        var userId = UUID.randomUUID();
        var jwt = Jwt.withTokenValue("token-value")
            .header("alg", "HS256")
            .subject(userId.toString())
            .build();
        when(jwtDecoder.decode("token-value")).thenReturn(jwt);

        var out = interceptor.preSend(connectMessage("Bearer token-value"), channel);

        var accessor = Objects.requireNonNull(MessageHeaderAccessor.getAccessor(out, StompHeaderAccessor.class));
        var user = Objects.requireNonNull(accessor.getUser());
        assertThat(user.getName()).isEqualTo(userId.toString());
    }

    @Test
    void connectWithoutAuthorizationHeaderThrows() {
        assertThatThrownBy(() -> interceptor.preSend(connectMessage(null), channel))
            .isInstanceOf(MessagingException.class);

        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void connectWithInvalidTokenThrows() {
        when(jwtDecoder.decode("bad-token")).thenThrow(
            new JwtValidationException("Invalid token", List.of(new OAuth2Error("invalid_token")))
        );

        assertThatThrownBy(() -> interceptor.preSend(connectMessage("Bearer bad-token"), channel))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void nonConnectFramePassesThroughWithoutAuth() {
        var accessor = StompHeaderAccessor.create(StompCommand.SEND);
        var message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        var out = interceptor.preSend(message, channel);

        assertThat(out).isSameAs(message);

        verifyNoInteractions(jwtDecoder);
    }

    private Message<byte[]> connectMessage(@Nullable String authorization) {
        var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        // Giữ accessor mutable như khi Spring parse frame CONNECT thật trong inbound channel,
        // nếu không setUser() sẽ văng IllegalStateException: Already immutable.
        accessor.setLeaveMutable(true);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
