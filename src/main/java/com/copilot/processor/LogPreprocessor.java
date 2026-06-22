package com.copilot.processor;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class LogPreprocessor {


    private static final int MAX_CHARS = 4000;

    public String preprocess(String rawLogs) {
        String[] lines = rawLogs.split("\\R");

        String firstBlock = extractFirstLines(lines, 5);
        String lastBlock = extractLastLines(lines, 5);
        String importantBlock = extractImportantLines(lines);

        int firstBudget = 800;
        int lastBudget = 1200;
        int importantBudget = MAX_CHARS - firstBudget - lastBudget;

        return """
                === LOG START CONTEXT ===
                %s

                === IMPORTANT LINES ===
                %s

                === LOG END CONTEXT ===
                %s
                """.formatted(
                trimFromStart(firstBlock, firstBudget),
                trimFromStart(importantBlock, importantBudget),
                trimFromEnd(lastBlock, lastBudget)
        );
    }

    private String extractFirstLines(String[] lines, int count) {
        return Arrays.stream(lines)
                .limit(count)
                .collect(Collectors.joining("\n"));
    }

    private String extractLastLines(String[] lines, int count) {
        return Arrays.stream(lines)
                .skip(Math.max(0, lines.length - count))
                .collect(Collectors.joining("\n"));
    }

    private String extractImportantLines(String[] lines) {
        return Arrays.stream(lines)
                .filter(this::isImportant)
                .collect(Collectors.joining("\n"));
    }

    private boolean isImportant(String line) {
        String upper = line.toUpperCase();

        return upper.contains("ERROR")
                || upper.contains("WARN")
                || upper.contains("EXCEPTION")
                || upper.contains("FAILED")
                || upper.contains("TIMEOUT")
                || upper.contains("REFUSED")
                || upper.contains("DENIED");
    }

    private String trimFromStart(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }

        return text.substring(0, maxChars) + "\n... [truncated]";
    }

    private String trimFromEnd(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }

        return "[truncated] ...\n" + text.substring(text.length() - maxChars);
    }

}
