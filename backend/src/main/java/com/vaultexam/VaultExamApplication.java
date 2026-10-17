package com.vaultexam;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class VaultExamApplication {

    public static void main(String[] args) {
        SpringApplication.run(VaultExamApplication.class, args);
    }

    /**
     * Options are set explicitly (not only in properties) so they can't be lost when
     * default options replace rather than merge. disableThinking() stops reasoning
     * models like qwen3.5 from returning an empty answer.
     */
    @Bean
    ChatClient chatClient(ChatClient.Builder builder,
                          @Value("${spring.ai.ollama.chat.options.model}") String model,
                          @Value("${spring.ai.ollama.chat.options.num-ctx:8192}") int numCtx,
                          @Value("${spring.ai.ollama.chat.options.temperature:0.2}") double temperature) {
        return builder
                .defaultOptions(OllamaChatOptions.builder()
                        .model(model)
                        .numCtx(numCtx)
                        .temperature(temperature)
                        .disableThinking())   // options builder: no .build() here
                .build();
    }

    @Bean
    WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:3000")
                        .allowedMethods("GET", "POST", "DELETE", "OPTIONS");
            }
        };
    }
}