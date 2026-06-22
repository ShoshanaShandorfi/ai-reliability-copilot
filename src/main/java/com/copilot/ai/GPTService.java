package com.copilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GPTService {

    @Value("${openai.api.key}")
    private String apiKey;



    public String analyze(Map<Object, Object> log) {
        try {
            String logText = String.format(
                    "service=%s, message=%s",
                    log.get("service"),
                    log.get("message")
            );

            String prompt = """
                You are a senior SRE.
                Analyze this log and return:
                1. Root cause
                2. Explanation
                3. Suggested fix

                Log:
                %s
                """.formatted(logText);

            // לא להדפיס API KEY אמיתי בלוגים
            System.out.println("API KEY loaded = " + (apiKey != null && !apiKey.isBlank()));

            URL url = new URL("https://api.openai.com/v1/chat/completions");

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            ObjectMapper mapper = new ObjectMapper();

            Map<String, Object> request = Map.of(
                    "model", "gpt-4o-mini",
                    "messages", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", prompt
                            )
                    ),
                    "max_tokens", 300
            );

            String body = mapper.writeValueAsString(request);

            System.out.println("REQUEST BODY: " + body);

            // ✅ שולחים BODY פעם אחת בלבד
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            System.out.println("Response code = " + code);

            // ✅ טיפול בשגיאות
            if (code != 200) {
                StringBuilder errorResponse = new StringBuilder();

                try (BufferedReader errorReader =
                             new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {

                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        System.out.println("ERROR BODY: " + line);
                        errorResponse.append(line);
                    }
                }

                if (code == 429) {
                    return "AI ERROR: OpenAI quota exceeded. Check billing/credits/quota.";
                }

                if (code == 401) {
                    return "AI ERROR: Invalid OpenAI API key.";
                }

                return "AI ERROR: HTTP " + code + " - " + errorResponse;
            }

            // ✅ רק אם 200 קוראים response רגיל
            StringBuilder response = new StringBuilder();

            try (BufferedReader br =
                         new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            JsonNode root = mapper.readTree(response.toString());

            return root
                    .get("choices")
                    .get(0)
                    .get("message")
                    .get("content")
                    .asText();

        } catch (Exception e) {
            e.printStackTrace();
            return "AI ERROR: " + e.getMessage();
        }
    }
}
