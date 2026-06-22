package com.copilot.queue;

import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

@Service
public class RedisProducer {

    private static final String STREAM_NAME = "logs_stream";

    private final StringRedisTemplate redisTemplate;

    public RedisProducer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void send(Map<String, String> data) {
        redisTemplate.opsForStream()
                .add(STREAM_NAME, data);
    }

}
