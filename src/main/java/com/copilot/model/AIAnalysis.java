package com.copilot.model;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIAnalysis {

    private String rootCause;
    private String explanation;
    private List<String> suggestions;
    private String severity;
    private String confidence;

    private String status;
    private String error;

    private List<String> missingFields;
    private String validationWarning;

}
