package com.copilot.repository;

import com.copilot.model.LogAnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface LogAnalysisJobRepository
        extends JpaRepository<LogAnalysisJob, String> {

    Optional<LogAnalysisJob> findByContentHash(String contentHash);
}