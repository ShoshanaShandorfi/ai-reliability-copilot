package com.copilot.controller;


import com.copilot.model.AIAnalysis;
import com.copilot.model.LogRequest;
import com.copilot.service.LogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @PostMapping
    public ResponseEntity<String> ingest(@RequestBody LogRequest log) {
        String id= logService.process(log);
        return ResponseEntity.ok(id);
    }


    @GetMapping("/analysis/{id}")
    public AIAnalysis getAnalysis(@PathVariable String id) {
        return logService.getResult(id);
    }


    @PostMapping("/file")
    public ResponseEntity<String> uploadLogFile(@RequestParam("file") MultipartFile file) {
        try {

            String content = new String(file.getBytes(), StandardCharsets.UTF_8);

            String id = logService.processFile(file.getOriginalFilename(), content);

            return ResponseEntity.ok(id);


        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to read file");
        }
    }



}
