package com.food.ordering.system.outbox.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulerConfig {
//    @Bean
//    @Primary
//    public ObjectMapper objectMapper() {
//        return new ObjectMapper()
//                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
//                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
//                .registerModule(new JavaTimeModule());
//    }
}
