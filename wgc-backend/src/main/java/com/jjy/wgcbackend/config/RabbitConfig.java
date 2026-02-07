package com.jjy.wgcbackend.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    // 队列名称
    public static final String QUEUE_NAME = "driver.track.queue";
    // 队列
    @Bean
    public Queue trackQueue() {
        return new Queue(QUEUE_NAME, true);
    }
    // 消息转换器
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
