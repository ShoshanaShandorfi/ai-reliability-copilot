package com.copilot.ai;


import com.copilot.model.AIAnalysis;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.openai.client.OpenAIClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AIService {


    private static final String OLLAMA_URL = "http://localhost:11434/api/chat";
    private static final String MODEL = "llama3.2";
    private static final int MAX_ATTEMPTS = 2;

    private final ObjectMapper mapper = new ObjectMapper();

    public AIAnalysis analyze(String content) {
        AIAnalysis bestPartialResult = null;
        String lastError = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                AIAnalysis analysis = callOllama(buildAnalysisPrompt(content));

                if (!hasRequiredFields(analysis)) {
                    lastError = "AI response missing required fields: "
                            + findMissingRequiredFields(analysis);

                    System.out.println("AI response invalid. attempt=" + attempt + ", reason=" + lastError);
                    continue;
                }

                AIAnalysis normalized = normalizeAnalysis(analysis);

                if ("DONE".equals(normalized.getStatus())) {
                    return normalized;
                }

                /*
                 * אם חסרים recommended fields כמו suggestions/confidence:
                 * לא ממציאים בקוד.
                 * עושים repair call ל-Ollama כדי שה-AI ישלים.
                 */
                AIAnalysis repaired = repairMissingFields(content, normalized);

                if (hasRequiredFields(repaired)) {
                    AIAnalysis repairedNormalized = normalizeAnalysis(repaired);

                    if ("DONE".equals(repairedNormalized.getStatus())) {
                        return repairedNormalized;
                    }

                    bestPartialResult = repairedNormalized;
                } else {
                    bestPartialResult = normalized;
                }

                System.out.println("AI response is partial. attempt=" + attempt
                        + ", missing=" + bestPartialResult.getMissingFields());

            } catch (Exception e) {
                lastError = e.getMessage();

                System.out.println("AI call failed. attempt=" + attempt + ", error=" + lastError);
                e.printStackTrace();
            }
        }

        if (bestPartialResult != null) {
            return bestPartialResult;
        }

        return AIAnalysis.builder()
                .status("ERROR")
                .error("AI failed to return valid analysis. Last error: " + lastError)
                .build();
    }

    private AIAnalysis repairMissingFields(String originalContent, AIAnalysis partial) {
        try {
            String repairPrompt = buildRepairPrompt(originalContent, partial);
            return callOllama(repairPrompt);

        } catch (Exception e) {
            System.out.println("AI repair call failed: " + e.getMessage());
            return partial;
        }
    }

    private AIAnalysis callOllama(String prompt) throws Exception {
        URL url = new URL(OLLAMA_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");

        Map<String, Object> request = Map.of(
                "model", MODEL,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                ),
                "stream", false,
                "format", "json"
        );

        String body = mapper.writeValueAsString(request);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();

        if (code != 200) {
            String errorBody = readErrorBody(conn);
            throw new RuntimeException("OLLAMA ERROR: HTTP " + code + " - " + errorBody);
        }

        String responseBody = readResponseBody(conn);

        JsonNode outerJson = mapper.readTree(responseBody);

        JsonNode messageNode = outerJson.get("message");

        if (messageNode == null || messageNode.get("content") == null) {
            throw new RuntimeException("Invalid Ollama response: missing message.content");
        }

        String innerContent = messageNode.get("content").asText();

        JsonNode structured = mapper.readTree(innerContent);

        return AIAnalysis.builder()
                .rootCause(getSafe(structured, "rootCause"))
                .explanation(getSafe(structured, "explanation"))
                .suggestions(getStringList(structured, "suggestions"))
                .severity(getSafe(structured, "severity"))
                .confidence(getSafe(structured, "confidence"))
                .build();
    }


    private String buildAnalysisPrompt(String content) {
        return """
                Analyze the following system logs
                
                 Root cause rules:
                 - Identify the most probable root cause based on the logs.
                 - Prefer the earliest clear failure event in the logs when possible.
                 - If a service fails while calling another service, consider whether the failure originates from the dependency or from the caller.
                 - If a failure explicitly mentions a component (e.g., Database connection refused, Gateway timeout), use that component as the root cause.
                 - Avoid over-generalizing root cause to a central component unless it is clearly the source of failure.                 
                
                 Root cause format rule:
                 - The rootCause must follow the format: "<Component> <FailureType>"
                   Examples:
                   - "Gateway timeout"
                   - "Database connection refused"
                   - "PaymentService internal failure"
                
                 - Do NOT return only the component name.
                 - Always include the failure type.                
                
                Terminology consistency rules:
                - Use consistent domain-oriented terminology.
                - Prefer standard service names such as:
                  PaymentService, Gateway, Database.
                - Do not invent multiple names for the same component.
                - Do not rename services that already appear in the logs.
                - Use the exact service names from the logs whenever possible.  
                
                
                Naming rules:
                - Always use exact service names from the logs (e.g., PaymentService, not "Payment Service")
                - Do not add spaces or change naming format.
                
                
                Suggestions rules:
                - suggestions is required.
                - suggestions must contain 2-3 concrete production mitigation steps.
                - Each suggestion must be directly relevant to the detected root cause.
                - Do not return an empty suggestions array.
                - Do not invent specific configuration values that are not present in the logs.
                - Do not include generic or unrelated optimizations.
                - Suggestions must focus on fixing the root cause, not merely masking symptoms.
                - Do not suggest mechanisms that are not present or clearly implied by the logs.                
                
                Severity rules:
                - severity must be exactly ONE of the following values:
                  LOW, MEDIUM, HIGH

                - Always return exactly one value.
                - HTTP 500 or server errors should be treated as HIGH severity unless clearly proven to be isolated.
                - Database connection failures (e.g., connection refused) are CRITICAL failures and MUST be HIGH severity.
                - Any timeout while calling another service MUST be classified as HIGH severity.

                Guidance:
                - If logs contain timeouts, failed retries, or cascading failures → severity is HIGH.
                - If there is a clear failure → do NOT choose LOW.

                Decision rule:
                - If multiple severity levels seem possible, choose HIGH.

                Formatting:
                - Do NOT return multiple values.
                - Do NOT use separators such as: |, /, comma, or ranges.
                
                Confidence rules:
                - confidence must be exactly ONE of the following values:
                  LOW, MEDIUM, HIGH
                
                - You MUST return exactly one confidence value.
                - You MUST NOT return multiple values or ranges.
                
                Strict constraints:
                - Do NOT use separators such as: |, /, comma, or "to"
                - Invalid examples:
                  "LOW|MEDIUM|HIGH"
                  "MEDIUM/HIGH"
                
                - If uncertain, you MUST still choose the single best value.
                
                Formatting rules:
                - Return values in UPPERCASE only for severity and confidence.
                
                Return ONLY valid JSON in this exact structure:
                
                {
                  "rootCause": "...",
                  "explanation": "...",
                  "suggestions": [
                    "...",
                    "..."
                  ],
                  "severity": "LOW" | "MEDIUM" | "HIGH",
                  "confidence": "LOW" | "MEDIUM" | "HIGH"
                }
                
                Important:
                - Both "severity" and "confidence" MUST contain exactly one value each.
                - Never return multiple options.
                - Never return combined values.
                
                Content:
                %s
                """.formatted(content);
    }

    private String buildRepairPrompt(String originalContent, AIAnalysis partial) {
        return """
                Complete the missing fields in the following AI analysis.
                
                Rules:
                1. Do NOT change rootCause unless it is empty.
                2. Do NOT change explanation unless it is empty.
                3. Do NOT change severity unless it is empty.
                4. Fill suggestions with 2-3 concrete production mitigation steps.
                5. Fill confidence as LOW, MEDIUM, or HIGH.
                6. Return ONLY valid JSON.
                7. Do not return empty fields.
                8. suggestions MUST be an array of strings.
                
                Original logs:
                %s
                
                Existing partial analysis:
                {
                  "rootCause": "%s",
                  "explanation": "%s",
                  "severity": "%s",
                  "confidence": "%s"
                }
                
                Required JSON output:
                
                {
                  "rootCause": "...",
                  "explanation": "...",
                  "suggestions": [
                    "...",
                    "..."
                  ],
                  "severity": "LOW|MEDIUM|HIGH",
                  "confidence": "LOW|MEDIUM|HIGH"
                }
                """.formatted(
                sanitizeForPrompt(originalContent),
                sanitizeForPrompt(partial.getRootCause()),
                sanitizeForPrompt(partial.getExplanation()),
                sanitizeForPrompt(partial.getSeverity()),
                sanitizeForPrompt(partial.getConfidence())
        );
    }

    private AIAnalysis normalizeAnalysis(AIAnalysis analysis) {
        normalizeSuggestions(analysis);

        if (isBlank(analysis.getConfidence())) {
            analysis.setConfidence(null);
        }

        List<String> missingRecommendedFields = findMissingRecommendedFields(analysis);

        if (missingRecommendedFields.isEmpty()) {
            analysis.setStatus("DONE");
            analysis.setMissingFields(null);
            analysis.setValidationWarning(null);
            return analysis;
        }

        analysis.setStatus("DONE_PARTIAL");
        analysis.setMissingFields(missingRecommendedFields);
        analysis.setValidationWarning("AI response is valid but missing recommended fields.");

        return analysis;
    }

    private void normalizeSuggestions(AIAnalysis analysis) {
        if (analysis.getSuggestions() == null) {
            return;
        }

        List<String> cleaned = analysis.getSuggestions()
                .stream()
                .filter(value -> value != null && !value.trim().isEmpty())
                .map(String::trim)
                .toList();

        if (cleaned.isEmpty()) {
            analysis.setSuggestions(null);
        } else {
            analysis.setSuggestions(cleaned);
        }
    }

    private boolean hasRequiredFields(AIAnalysis analysis) {
        return notBlank(analysis.getRootCause())
                && notBlank(analysis.getExplanation())
                && notBlank(analysis.getSeverity());
    }

    private List<String> findMissingRequiredFields(AIAnalysis analysis) {
        List<String> missing = new ArrayList<>();

        if (isBlank(analysis.getRootCause())) {
            missing.add("rootCause");
        }

        if (isBlank(analysis.getExplanation())) {
            missing.add("explanation");
        }

        if (isBlank(analysis.getSeverity())) {
            missing.add("severity");
        }

        return missing;
    }

    private List<String> findMissingRecommendedFields(AIAnalysis analysis) {
        List<String> missing = new ArrayList<>();

        if (analysis.getSuggestions() == null || analysis.getSuggestions().isEmpty()) {
            missing.add("suggestions");
        }

        if (isBlank(analysis.getConfidence())) {
            missing.add("confidence");
        }

        return missing;
    }

    private String getSafe(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }

        String value = node.get(field).asText();

        return isBlank(value) ? null : value;
    }

    private List<String> getStringList(JsonNode node, String field) {
        if (node == null || !node.has(field) || !node.get(field).isArray()) {
            return null;
        }

        List<String> values = new ArrayList<>();

        for (JsonNode item : node.get(field)) {
            String value = item.asText();

            if (value != null && !value.trim().isEmpty()) {
                values.add(value.trim());
            }
        }

        return values.isEmpty() ? null : values;
    }

    private String readResponseBody(HttpURLConnection conn) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

            return reader.lines().collect(Collectors.joining());
        }
    }

    private String readErrorBody(HttpURLConnection conn) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {

            return reader.lines().collect(Collectors.joining());

        } catch (Exception e) {
            return "Failed to read error body";
        }
    }

    private String sanitizeForPrompt(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "'")
                .replace("\r", " ")
                .replace("\n", "\\n");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean notBlank(String value) {
        return !isBlank(value);
    }

}