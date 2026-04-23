package com.smu.healyx.gpt.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/** OpenAI Chat Completions API 요청 DTO (Function Calling 지원) */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GptChatRequest {

    private String model;
    private List<Message> messages;

    @JsonProperty("max_completion_tokens")
    private int maxTokens;

    private double temperature;

    private List<GptTool> tools;

    @JsonProperty("tool_choice")
    private String toolChoice;

    /** 기존 단순 호출용 생성자 */
    public GptChatRequest(String model, List<Message> messages, int maxTokens, double temperature) {
        this.model = model;
        this.messages = messages;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }

    /** Agent Tool Calling 호출용 생성자 */
    public GptChatRequest(String model, List<Message> messages, int maxTokens, double temperature,
                          List<GptTool> tools, String toolChoice) {
        this(model, messages, maxTokens, temperature);
        this.tools = tools;
        this.toolChoice = toolChoice;
    }

    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        private String role;
        private String content;

        @JsonProperty("tool_call_id")
        private String toolCallId;

        @JsonProperty("tool_calls")
        private List<GptToolCall> toolCalls;

        /** system / user / assistant(텍스트) 메시지 */
        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        /** Tool 실행 결과 메시지 (role=tool) */
        public static Message ofToolResult(String toolCallId, String content) {
            Message m = new Message("tool", content);
            m.toolCallId = toolCallId;
            return m;
        }

        /** Assistant가 tool_calls를 반환한 메시지 (대화 히스토리 유지용) */
        public static Message ofAssistantToolCalls(List<GptToolCall> toolCalls) {
            Message m = new Message("assistant", null);
            m.toolCalls = toolCalls;
            return m;
        }
    }
}
