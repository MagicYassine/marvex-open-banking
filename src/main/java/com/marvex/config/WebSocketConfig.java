package com.marvex.config;

import com.marvex.constants.PaymentConstants;
import com.marvex.entity.Merchant;
import com.marvex.repository.MerchantRepository;
import com.marvex.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;
    private final MerchantRepository merchantRepository;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String token = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
                    if (token != null && token.startsWith(JwtUtil.BEARER_PREFIX)) {
                        token = token.substring(JwtUtil.BEARER_PREFIX.length());
                        if (jwtUtil.isValid(token)) {
                            var merchantId = jwtUtil.extractMerchantId(token);
                            merchantRepository.findById(merchantId).ifPresent(merchant -> {
                                accessor.setUser(new UsernamePasswordAuthenticationToken(merchant, null, List.of()));
                                log.debug("[WS] Authenticated merchantId={}", merchantId);
                            });
                        } else {
                            log.warn("[WS] Invalid or expired JWT on CONNECT");
                            return errorFrame("Authentication failed: invalid or expired token");
                        }
                    }
                }

                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    if (destination != null && destination.startsWith(PaymentConstants.PAYMENT_TOPIC_PREFIX)) {
                        String requestedId = destination.substring(PaymentConstants.PAYMENT_TOPIC_PREFIX.length());
                        var principal = accessor.getUser();
                        if (principal == null) {
                            log.warn("[WS] Unauthenticated subscription attempt to {}", destination);
                            return errorFrame("Authentication required");
                        }
                        Merchant merchant = (Merchant) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
                        if (!merchant.getId().toString().equals(requestedId)) {
                            log.warn("[WS] merchantId={} attempted to subscribe to {}", merchant.getId(), destination);
                            return errorFrame("Forbidden");
                        }
                    }
                }

                return message;
            }

            private Message<?> errorFrame(String message) {
                StompHeaderAccessor error = StompHeaderAccessor.create(StompCommand.ERROR);
                error.setMessage(message);
                error.setLeaveMutable(true);
                return MessageBuilder.createMessage(new byte[0], error.getMessageHeaders());
            }
        });
    }
}
