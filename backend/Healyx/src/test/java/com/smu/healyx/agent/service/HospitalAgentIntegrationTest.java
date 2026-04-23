package com.smu.healyx.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smu.healyx.agent.dto.HospitalAssistantRequest;
import com.smu.healyx.agent.dto.HospitalAssistantResponse;
import com.smu.healyx.gpt.service.GptService;
import com.smu.healyx.hira.service.HiraApiService;
import com.smu.healyx.user.dto.UserProfileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * HospitalAgent 실제 API 통합 테스트
 *
 * GPT와 HIRA API를 실제로 호출합니다.
 * 환경변수 GPT_API_KEY, HIRA_API_URL, HIRA_API_KEY 가 설정되지 않으면 자동으로 건너뜁니다.
 * CI 환경에서는 실행하지 않도록 주의하세요 (API 비용·쿼터 소모).
 */
@DisplayName("HospitalAgent 실제 API 통합 테스트")
class HospitalAgentIntegrationTest {

    private HospitalAgentService agentService;

    private static final String GPT_KEY     = System.getenv("GPT_API_KEY");
    private static final String HIRA_URL    = System.getenv("HIRA_API_URL");
    private static final String HIRA_KEY    = System.getenv("HIRA_API_KEY");

    // 서울 시청 좌표 (테스트 기준 위치)
    private static final double TEST_LAT = 37.5665;
    private static final double TEST_LNG = 126.9780;

    @BeforeEach
    void setUp() {
        assumeTrue(GPT_KEY  != null && !GPT_KEY.isBlank(),  "GPT_API_KEY 미설정 — 테스트 건너뜀");
        assumeTrue(HIRA_URL != null && !HIRA_URL.isBlank(), "HIRA_API_URL 미설정 — 테스트 건너뜀");
        assumeTrue(HIRA_KEY != null && !HIRA_KEY.isBlank(), "HIRA_API_KEY 미설정 — 테스트 건너뜀");

        ObjectMapper objectMapper = new ObjectMapper();

        GptService gptService = new GptService(new RestTemplate(), objectMapper);
        ReflectionTestUtils.setField(gptService, "gptApiKey", GPT_KEY);

        HiraApiService hiraApiService = new HiraApiService(new RestTemplate(), objectMapper);
        ReflectionTestUtils.setField(hiraApiService, "hiraApiUrl", HIRA_URL);
        ReflectionTestUtils.setField(hiraApiService, "hiraApiKey", HIRA_KEY);

        agentService = new HospitalAgentService(gptService, hiraApiService, objectMapper);
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────

    private HospitalAssistantRequest buildRequest(String symptom, int riskLevel) {
        HospitalAssistantRequest req = new HospitalAssistantRequest();
        ReflectionTestUtils.setField(req, "symptom", symptom);
        ReflectionTestUtils.setField(req, "riskLevel", riskLevel);
        ReflectionTestUtils.setField(req, "latitude", TEST_LAT);
        ReflectionTestUtils.setField(req, "longitude", TEST_LNG);
        return req;
    }

    private void printResult(String title, HospitalAssistantResponse result) {
        System.out.println("\n============================");
        System.out.println(" [" + title + "]");
        System.out.println(" 진료과: " + result.getDepartmentName() + " (" + result.getDepartmentCode() + ")");
        System.out.println(" ICD-10: " + result.getIcd10Code());
        System.out.printf(" 병원 수: %d건 (총 %d건 중)%n",
                result.getHospitals().getHospitals().size(),
                result.getHospitals().getTotalCount());
        result.getHospitals().getHospitals().stream().limit(3).forEach(h ->
                System.out.printf("   %-6s | %-20s | %s%n",
                        h.getHospitalType(), h.getHospitalName(), h.getAddress()));
        System.out.println("============================\n");
    }

    // ── 테스트 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("[실제 API] 한국어 증상 + 위험도 2 → 진료과·병원·ICD-10 반환")
    void korean_riskLevel2_returnsFullResult() {
        HospitalAssistantResponse result = agentService.run(
                buildRequest("목이 아프고 열이 납니다", 2),
                UserProfileDto.builder().age(30).gender("M").insured(true).build());

        printResult("한국어 증상 / 위험도 2", result);

        assertThat(result.getDepartmentCode()).isNotBlank();
        assertThat(result.getDepartmentName()).isNotBlank();
        assertThat(result.getIcd10Code()).matches("[A-Z]\\d+.*");
        assertThat(result.getHospitals().getHospitals()).isNotEmpty();
    }

    @Test
    @DisplayName("[실제 API] 영어 증상 + 위험도 4 → 종합병원·상급종합 범위 병원 반환")
    void english_riskLevel4_returnsHighLevelHospitals() {
        HospitalAssistantResponse result = agentService.run(
                buildRequest("I have severe chest pain and difficulty breathing", 4),
                UserProfileDto.builder().age(45).gender("F").insured(false).build());

        printResult("영어 증상 / 위험도 4", result);

        assertThat(result.getDepartmentCode()).isNotBlank();
        assertThat(result.getIcd10Code()).isNotBlank();
        // 위험도 4 → clCd=11(종합병원), 01(상급종합)만 조회
        result.getHospitals().getHospitals().forEach(h ->
                assertThat(h.getHospitalType()).isIn("종합병원", "상급종합"));
    }

    @Test
    @DisplayName("[실제 API] 게스트 사용자 — ICD-10 코드만으로 기본 결과 반환")
    void guest_returnsBasicResult() {
        HospitalAssistantResponse result = agentService.run(
                buildRequest("배가 아프고 구토가 납니다", 1),
                UserProfileDto.guestDefault());

        printResult("게스트 / 위험도 1", result);

        assertThat(result.getDepartmentCode()).isNotBlank();
        assertThat(result.getIcd10Code()).isNotBlank();
        assertThat(result.getHospitals().getHospitals()).isNotEmpty();
    }

    @Test
    @DisplayName("[실제 API] 위험도 5 (응급) — 상급종합병원만 조회된다")
    void emergency_riskLevel5_returnsOnlyTopHospitals() {
        HospitalAssistantResponse result = agentService.run(
                buildRequest("의식을 잃었다가 깨어났습니다", 5),
                UserProfileDto.builder().age(60).gender("M").insured(true).build());

        printResult("응급 / 위험도 5", result);

        assertThat(result.getDepartmentCode()).isNotBlank();
        result.getHospitals().getHospitals().forEach(h ->
                assertThat(h.getHospitalType()).isEqualTo("상급종합"));
    }
}
