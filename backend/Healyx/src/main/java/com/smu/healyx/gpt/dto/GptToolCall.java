package com.smu.healyx.gpt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** OpenAI Function Calling 응답/요청의 tool_calls 항목 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GptToolCall {

    private String id;
    private String type;
    private FunctionCall function;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionCall {
        private String name;
        private String arguments; // JSON 문자열
    }
}
