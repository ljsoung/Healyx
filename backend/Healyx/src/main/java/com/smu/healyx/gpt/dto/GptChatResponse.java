package com.smu.healyx.gpt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** OpenAI Chat Completions API 응답 DTO (Function Calling 지원) */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GptChatResponse {

    private List<Choice> choices;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Message message;

        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String role;
        private String content;

        @JsonProperty("tool_calls")
        private List<GptToolCall> toolCalls;
    }

    public String getFirstContent() {
        if (choices == null || choices.isEmpty()) return null;
        Choice choice = choices.get(0);
        if (choice.getMessage() == null) return null;
        return choice.getMessage().getContent();
    }

    public String getFinishReason() {
        if (choices == null || choices.isEmpty()) return null;
        return choices.get(0).getFinishReason();
    }

    public List<GptToolCall> getToolCalls() {
        if (choices == null || choices.isEmpty()) return null;
        Choice choice = choices.get(0);
        if (choice.getMessage() == null) return null;
        return choice.getMessage().getToolCalls();
    }
}
