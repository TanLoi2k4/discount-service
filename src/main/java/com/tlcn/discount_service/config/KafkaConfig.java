package com.tlcn.discount_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:discount-service-group}")
    private String groupId;
    
    // Lấy giá trị từ application.yml
    @Value("${spring.kafka.consumer.properties.spring.json.trusted.packages:java.util.*,com.tlcn.*}")
    private String trustedPackages; 

    // --- Cấu hình Producer ---
    // Sử dụng JsonSerializer/StringSerializer như đã khai báo trong application.yml
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class); // Dùng JsonSerializer
        // Cross-service DTO compatibility: publish type id as alias instead of fully-qualified class name
        // so consumers can map it to their local DTO class.
        // NOTE:
        // - flashSaleEvent: used when discount consumes events produced by flashsale (cross-service)
        // - discountCommand: used when discount publishes/consumes its own command messages with stable alias
        config.put(JsonSerializer.TYPE_MAPPINGS,
                "flashSaleEvent:com.tlcn.discount_service.dto.kafka.FlashSaleEvent,"
                + "discountCommand:com.tlcn.discount_service.dto.kafka.DiscountCommand,"
                + "discountEvent:com.tlcn.discount_service.dto.kafka.DiscountEvent");

        // Use the app ObjectMapper (with JavaTimeModule) for Kafka JSON serialization
        return new DefaultKafkaProducerFactory<>(
                config,
                new StringSerializer(),
                new JsonSerializer<>(objectMapper())
        );
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() { // Thay đổi Value Type sang Object
        return new KafkaTemplate<>(producerFactory());
    }

    // --- Cấu hình Consumer ---
    // Sử dụng StringDeserializer/JsonDeserializer như đã khai báo trong application.yml
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class); // Dùng JsonDeserializer
        config.put(JsonDeserializer.TRUSTED_PACKAGES, trustedPackages);

        config.put(JsonDeserializer.TYPE_MAPPINGS,
                "flashSaleEvent:com.tlcn.discount_service.dto.kafka.FlashSaleEvent,"
                + "discountCommand:com.tlcn.discount_service.dto.kafka.DiscountCommand,"
                + "discountEvent:com.tlcn.discount_service.dto.kafka.DiscountEvent");
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // Vẫn giữ cấu hình Manual Acknowledge từ application.yml
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // Use the app ObjectMapper (with JavaTimeModule) for Kafka JSON deserialization
        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                new JsonDeserializer<>(Object.class, objectMapper())
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() { // Thay đổi Value Type sang Object
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setBatchListener(false); // Xử lý từng tin nhắn
        factory.getContainerProperties().setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE); // Kích hoạt Manual Acknowledge
        return factory;
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}