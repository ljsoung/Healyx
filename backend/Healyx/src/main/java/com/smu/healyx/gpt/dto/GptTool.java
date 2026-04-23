package com.smu.healyx.gpt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/** OpenAI Function Calling 도구 정의 */
@Getter
@AllArgsConstructor
public class GptTool {

    private final String type = "function";
    private final Function function;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Function {
        private String name;
        private String description;
        private Map<String, Object> parameters;
    }
}
