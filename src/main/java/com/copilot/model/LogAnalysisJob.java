package com.copilot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "log_analysis_jobs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_log_analysis_content_hash",
                        columnNames = "content_hash"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogAnalysisJob {

    @Id
    private String id;

    private String fileName;

    @Column(name = "content_hash", nullable = false, unique = true)
    private String contentHash;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String preparedContent;

    @Column(columnDefinition = "TEXT")
    private String rootCause;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(columnDefinition = "TEXT")
    private String suggestionsJson;

    private String severity;

    private String confidence;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(columnDefinition = "TEXT")
    private String validationWarning;

    @Column(columnDefinition = "TEXT")
    private String missingFieldsJson;

    private Instant createdAt;
    private Instant updatedAt;
}