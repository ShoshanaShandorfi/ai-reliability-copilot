package com.copilot.queue;

import com.copilot.ai.AIService;
import com.copilot.model.AIAnalysis;
import com.copilot.service.LogService;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

@Component
public class LogConsumer {

    private static final String STREAM_NAME = "logs_stream";
    private static final String GROUP_NAME = "group1";
    private static final String CONSUMER_NAME = "consumer1";

    private final StringRedisTemplate redisTemplate;
    private final AIService aiService;
    private final LogService logService;

    public LogConsumer(StringRedisTemplate redisTemplate, AIService aiService, LogService logService) {
        this.redisTemplate = redisTemplate;
        this.aiService = aiService;
        this.logService = logService;
    }

    @PostConstruct
    public void consume() {
        System.out.println("✅ Consumer bean created");

        ensureStreamAndGroup();

        Executors.newSingleThreadExecutor().submit(() -> {
            System.out.println("✅ Consumer thread started");

            while (true) {
                try {
                    System.out.println("Polling Redis stream...");

                    List<MapRecord<String, Object, Object>> messages =
                            redisTemplate.opsForStream().read(
                                    Consumer.from(GROUP_NAME, CONSUMER_NAME),
                                    StreamReadOptions.empty()
                                            .count(1)
                                            .block(Duration.ofSeconds(2)),
                                    StreamOffset.create(STREAM_NAME, ReadOffset.lastConsumed())
                            );

                    if (messages == null || messages.isEmpty()) {
                        continue;
                    }

                    for (MapRecord<String, Object, Object> record : messages) {
                        System.out.println("✅ Message received from Redis: " + record.getValue());

                        handle(record.getValue());

                        redisTemplate.opsForStream()
                                .acknowledge(STREAM_NAME, GROUP_NAME, record.getId());

                        System.out.println("✅ Message acknowledged: " + record.getId());
                    }

                } catch (Exception e) {
                    System.out.println("❌ Error in Redis consumer loop:");
                    e.printStackTrace();

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    private void ensureStreamAndGroup() {
        try {
            redisTemplate.opsForStream().add(STREAM_NAME, Map.of(
                    "type", "init",
                    "message", "stream initialization"
            ));

            redisTemplate.opsForStream()
                    .createGroup(STREAM_NAME, ReadOffset.from("0-0"), GROUP_NAME);

            System.out.println("✅ Redis consumer group created");

        } catch (Exception e) {
            String message = e.getMessage();

            if (message != null && message.contains("BUSYGROUP")) {
                System.out.println("ℹ️ Redis consumer group already exists");
            } else {
                System.out.println("❌ Failed to create Redis consumer group");
                e.printStackTrace();
            }
        }
    }



    private void handle(Map<Object, Object> log) {
        System.out.println("🔥 Inside handle(): " + log);

        if ("init".equals(log.get("type"))) {
            System.out.println("Skipping init message");
            return;
        }

        String id = (String) log.get("id");

        String content = logService.getPreparedContent(id);

        if (content == null) {
            logService.saveResult(id, AIAnalysis.builder()
                    .status("ERROR")
                    .error("Prepared content not found for job id: " + id)
                    .build());
            return;
        }

        AIAnalysis analysis = aiService.analyze(content);

        if (analysis.getStatus() == null) {
            if (analysis.getError() != null) {
                analysis.setStatus("ERROR");
            }/* else {
                analysis.setStatus("DONE");
            }*/
        }

        logService.saveResult(id, analysis);

        System.out.println("✅ Saved result for ID: " + id);
    }


}