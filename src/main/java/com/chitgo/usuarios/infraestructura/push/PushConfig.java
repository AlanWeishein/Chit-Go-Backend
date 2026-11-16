package com.chitgo.usuarios.infraestructura.push;

import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(PushProperties.class)
class PushConfig {
    @Bean("enviosPush")
    ThreadPoolTaskExecutor enviosPush() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("web-push-");
        executor.setRejectedExecutionHandler((tarea, pool) ->
                LoggerFactory.getLogger(PushConfig.class).warn("Cola Push llena: se descarta una notificación."));
        return executor;
    }
}
