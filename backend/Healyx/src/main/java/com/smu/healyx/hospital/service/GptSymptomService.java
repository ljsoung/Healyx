package com.smu.healyx.hospital.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smu.healyx.hospital.dto.SymptomAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GptSymptomService {

    @Value("${api.gpt.key}")
    private String gptApiKey;

    private static final String GPT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL   = "gpt-4o-mini";

    private final ObjectMapper objectMapper;

    /** GPT에 전달할 시스템 프롬프트. 진료과명은 hospital_departments 테이블 실제 값과 일치해야 함 */
    private static final String SYSTEM_PROMPT = """
            You are a Korean medical triage assistant.
            Analyze the symptom and respond ONLY with valid JSON — no markdown, no explanation.
            
            Rules:
            - riskLevel: integer 1~5 (1=very mild, 5=emergency)
            - department: one of the following Korean department names exactly:
              내과, 외과, 정형외과, 신경과, 신경외과, 소아청소년과, 산부인과,
              비뇨의학과, 안과, 이비인후과, 피부과, 정신건강의학과, 응급의학과,
              흉부외과, 성형외과, 재활의학과, 가정의학과, 치과, 한의과
            
            Required format: {"riskLevel": <number>, "department": "<string>"}
            """;

    public SymptomAnalysisResult analyzeSymptom(String symptom) {
        try {
            RestClient client = RestClient.builder()
                    .baseUrl(GPT_URL)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + gptApiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user",   "content", symptom)
                    ),
                    "response_format", Map.of("type", "json_object"),
                    "max_tokens", 100,
                    "temperature", 0.3
            );

            String raw = client.post()
                    .body(body)
                    .retrieve()
                    .body(String.class);

            String content = objectMapper.readTree(raw)
                    .path("choices").get(0)
                    .path("message").path("content").asText();

            var node = objectMapper.readTree(content);
            int riskLevel  = Math.max(1, Math.min(5, node.path("riskLevel").asInt(2)));
            String dept    = node.path("department").asText("내과");

            return new SymptomAnalysisResult(riskLevel, dept);

        } catch (Exception e) {
            log.error("GPT symptom analysis failed: {}", e.getMessage());
            return new SymptomAnalysisResult(2, "내과");  // fallback
        }
    }
}