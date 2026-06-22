package com.copilot.service;

import com.copilot.model.AIAnalysis;
import com.copilot.model.LogAnalysisJob;
import com.copilot.model.LogRequest;
import com.copilot.processor.LogPreprocessor;
import com.copilot.queue.RedisProducer;
import com.copilot.repository.LogAnalysisJobRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.hash;

@Service
public class LogService {


    private final RedisProducer producer;
    private final LogPreprocessor logPreprocessor;
    private final LogAnalysisJobRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    public LogService(RedisProducer producer,
                      LogPreprocessor logPreprocessor,
                      LogAnalysisJobRepository repository) {
        this.producer = producer;
        this.logPreprocessor = logPreprocessor;
        this.repository = repository;
    }

    public String processFile(String fileName, String rawContent) {

        String contentHash = hash(rawContent);

        return repository.findByContentHash(contentHash)
                .map(LogAnalysisJob::getId)
                .orElseGet(() -> createAndEnqueueJobSafely(fileName, rawContent, contentHash));
    }


    private String createAndEnqueueJobSafely(String fileName, String rawContent, String contentHash) {
        try {
            return createAndEnqueueJob(fileName, rawContent, contentHash);

        } catch (DataIntegrityViolationException duplicate) {
            // מישהו אחר כבר הכניס את אותו hash במקביל
            return repository.findByContentHash(contentHash)
                    .map(LogAnalysisJob::getId)
                    .orElseThrow(() -> duplicate);
        }
    }




    public String process(LogRequest log) {
        String rawContent = """
            service=%s
            message=%s
            timestamp=%s
            """.formatted(log.service, log.message, log.timestamp);

        String contentHash = hash(rawContent);

        return repository.findByContentHash(contentHash)
                .map(LogAnalysisJob::getId)
                .orElseGet(() -> createAndEnqueueJobSafely("inline-log", rawContent, contentHash));
    }



    private String createAndEnqueueJob(String fileName, String rawContent, String contentHash) {
        String id = UUID.randomUUID().toString();

        String preparedContent = logPreprocessor.preprocess(rawContent);

        LogAnalysisJob job = LogAnalysisJob.builder()
                .id(id)
                .fileName(fileName)
                .contentHash(contentHash)
                .preparedContent(preparedContent)
                .status("PROCESSING")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        repository.saveAndFlush(job);

        producer.send(Map.of("id", id));

        return id;
    }


    public String getPreparedContent(String id) {
        return repository.findById(id)
                .map(LogAnalysisJob::getPreparedContent)
                .orElse(null);
    }

    public void saveResult(String id, AIAnalysis analysis) {
        LogAnalysisJob job = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + id));

        job.setStatus(analysis.getStatus());
        job.setRootCause(analysis.getRootCause());
        job.setExplanation(analysis.getExplanation());
        job.setSuggestionsJson(toJson(analysis.getSuggestions()));
        job.setSeverity(analysis.getSeverity());
        job.setConfidence(analysis.getConfidence());
        job.setError(analysis.getError());
        job.setMissingFieldsJson(toJson(analysis.getMissingFields()));
        job.setValidationWarning(analysis.getValidationWarning());
        job.setUpdatedAt(Instant.now());

        repository.save(job);
    }

    public AIAnalysis getResult(String id) {
        return repository.findById(id)
                .map(this::toAIAnalysis)
                .orElse(null);
    }

    private AIAnalysis toAIAnalysis(LogAnalysisJob job) {
        return AIAnalysis.builder()
                .status(job.getStatus())
                .rootCause(job.getRootCause())
                .explanation(job.getExplanation())
                .suggestions(fromJsonList(job.getSuggestionsJson()))
                .severity(job.getSeverity())
                .confidence(job.getConfidence())
                .error(job.getError())
                .missingFields(fromJsonList(job.getMissingFieldsJson()))
                .validationWarning(job.getValidationWarning())
                .build();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize value", e);
        }
    }

    private List<String> fromJsonList(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            return mapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    private String hash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encoded);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash content", e);
        }
    }

}
