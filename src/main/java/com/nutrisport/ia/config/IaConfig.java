package com.nutrisport.ia.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cliente de Spring AI para el proveedor real (§5.7). Solo existe con {@code app.ia.modo=real}: en modo
 * simulado ningún componente puede llamar al proveedor.
 */
@Configuration
@ConditionalOnProperty(name = "app.ia.modo", havingValue = "real")
public class IaConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder constructor) {
        return constructor.build();
    }
}
