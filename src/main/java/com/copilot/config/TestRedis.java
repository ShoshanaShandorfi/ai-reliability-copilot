package com.copilot.config;

import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class TestRedis {

    private final StringRedisTemplate redisTemplate;

    public TestRedis(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void test() {
        redisTemplate.opsForValue().set("test", "hello");
        System.out.println(redisTemplate.opsForValue().get("test"));
    }

}
