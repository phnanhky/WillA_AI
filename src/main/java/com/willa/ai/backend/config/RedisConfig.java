package com.willa.ai.backend.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * Spring Boot's ObjectMapper already registers {@code JavaTimeModule} for {@code LocalDateTime}, etc.
     * A copied mapper without default typing deserializes cached DTOs as {@code LinkedHashMap},
     * which breaks {@code @Cacheable} return types (e.g. {@code PlanResponse}).
     */
    @Bean
    public GenericJackson2JsonRedisSerializer redisJsonSerializer(ObjectMapper objectMapper) {
        ObjectMapper redisMapper = objectMapper.copy();
        redisMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.willa.ai.backend")
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(redisMapper);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            GenericJackson2JsonRedisSerializer redisJsonSerializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(redisJsonSerializer);
        template.setHashValueSerializer(redisJsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisCacheConfiguration cacheConfiguration(GenericJackson2JsonRedisSerializer redisJsonSerializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(60))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisJsonSerializer));
    }
}
