package com.swiftmatch.indexer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Overrides the default Spring Boot listener factory to install a bounded
 * exponential-backoff retry per [SRS-LOC-7]: 100 ms, 200 ms, 400 ms, then a
 * no-op recoverer that logs ERROR and skips the record. DLT intentionally
 * omitted per Amendment 001 §4.2 (BL-2).
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);

        ExponentialBackOff backOff = new ExponentialBackOff(100L, 2.0);
        // 100 + 200 + 400 = 700 ms total budget; stops after the third retry.
        backOff.setMaxElapsedTime(700L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> log.error(
                        "location indexing failed after retries topic={} partition={} offset={} key={} cause={}",
                        record.topic(), record.partition(), record.offset(), record.key(),
                        exception.toString()),
                backOff);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
