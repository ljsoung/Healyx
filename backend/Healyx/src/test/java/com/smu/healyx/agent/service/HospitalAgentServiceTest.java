package com.smu.healyx.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smu.healyx.agent.dto.HospitalAssistantRequest;
import com.smu.healyx.agent.dto.HospitalAssistantResponse;
import com.smu.healyx.common.exception.ExternalApiException;
import com.smu.healyx.gpt.dto.GptChatResponse;
import com.smu.healyx.gpt.service.GptService;
import com.smu.healyx.hira.dto.HospitalDto;
import com.smu.healyx.hira.dto.HospitalSearchRequest;
import com.smu.healyx.hira.dto.HospitalSearchResponse;
import com.smu.healyx.hira.service.HiraApiService;
import com.smu.healyx.user.dto.UserProfileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * HospitalAgentService 단위 테스트
 *
 * GPT와 HIRA API를 모두 Mock 처리하여 Spring 컨텍스트 없이 실행됩니다.
 * 검증 범위:
 *   - Agent Loop 정상 흐름 (로그인 / 게스트)
 *   - 위험도별 clCd 범위 및 반경 매핑
 *   - HIRA 다중 호출 + ykiho 중복 제거
 *   - Tool 순차 호출 (2라운드)
 *   - HIRA 부분 실패 복원
 *   - 병원 미탐색 시 예외 처리
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HospitalAgentService 단위 테스트")
class HospitalAgentServiceTest {

    @Mock
    private GptService gptService;

    @Mock
    private HiraApiService hiraApiService;

    private HospitalAgentService agentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        agentService = new HospitalAgentService(gptService, hiraApiService, objectMapper);
    }

    // ── 헬퍼: 요청 객체 ────────────────────────────────────────────────

    private HospitalAssistantRequest buildRequest(String symptom, int riskLevel) {
        HospitalAssistantRequest req = new HospitalAssistantRequest();
        ReflectionTestUtils.setField(req, "symptom", symptom);
        ReflectionTestUtils.setField(req, "riskLevel", riskLevel);
        ReflectionTestUtils.setField(req, "latitude", 37.5665);
        ReflectionTestUtils.setField(req, "longitude", 126.9780);
        return req;
    }

    private UserProfileDto loginProfile() {
        return UserProfileDto.builder().age(30).gender("M").insured(true).build();
    }

    // ── 헬퍼: GPT 응답 객체 ───────────────────────────────────────────

    /** GPT가 두 Tool을 한 번에 호출하는 응답 */
    private GptChatResponse bothToolsResponse() throws Exception {
        String json = """
                {
                  "choices": [{
                    "finish_reason": "tool_calls",
                    "message": {
                      "role": "assistant",
                      "tool_calls": [
                        {
                          "id": "call_search",
                          "type": "function",
                          "function": {
                            "name": "search_hospitals",
                            "arguments": "{\\"dgsbjtCd\\":\\"12\\",\\"departmentName\\":\\"이비인후과\\"}"
                          }
                        },
                        {
                          "id": "call_icd10",
                          "type": "function",
                          "function": {
                            "name": "extract_icd10_code",
                            "arguments": "{\\"icd10Code\\":\\"J06.9\\"}"
                          }
                        }
                      ]
                    }
                  }]
                }
                """;
        return objectMapper.readValue(json, GptChatResponse.class);
    }

    /** GPT가 search_hospitals만 호출하는 응답 */
    private GptChatResponse searchOnlyResponse() throws Exception {
        String json = """
                {
                  "choices": [{
                    "finish_reason": "tool_calls",
                    "message": {
                      "role": "assistant",
                      "tool_calls": [{
                        "id": "call_search",
                        "type": "function",
                        "function": {
                          "name": "search_hospitals",
                          "arguments": "{\\"dgsbjtCd\\":\\"01\\",\\"departmentName\\":\\"내과\\"}"
                        }
                      }]
                    }
                  }]
                }
                """;
        return objectMapper.readValue(json, GptChatResponse.class);
    }

    /** GPT가 extract_icd10_code만 호출하는 응답 */
    private GptChatResponse icd10OnlyResponse() throws Exception {
        String json = """
                {
                  "choices": [{
                    "finish_reason": "tool_calls",
                    "message": {
                      "role": "assistant",
                      "tool_calls": [{
                        "id": "call_icd10",
                        "type": "function",
                        "function": {
                          "name": "extract_icd10_code",
                          "arguments": "{\\"icd10Code\\":\\"J06.9\\"}"
                        }
                      }]
                    }
                  }]
                }
                """;
        return objectMapper.readValue(json, GptChatResponse.class);
    }

    /** GPT가 stop을 반환하는 응답 */
    private GptChatResponse stopResponse() throws Exception {
        return objectMapper.readValue("""
                {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"완료"}}]}
                """, GptChatResponse.class);
    }

    // ── 헬퍼: HIRA 응답 객체 ──────────────────────────────────────────

    private HospitalSearchResponse hospitalResponse(String... ykihos) {
        List<HospitalDto> list = java.util.Arrays.stream(ykihos)
                .map(y -> HospitalDto.builder()
                        .ykiho(y)
                        .hospitalName("병원-" + y)
                        .address("서울")
                        .build())
                .toList();
        return HospitalSearchResponse.builder()
                .hospitals(list)
                .totalCount(list.size())
                .pageNo(1)
                .numOfRows(list.size())
                .build();
    }

    // ══════════════════════════════════════════════════════════════════
    // 1. Agent Loop 정상 흐름
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Agent Loop 정상 흐름")
    class NormalFlow {

        @Test
        @DisplayName("로그인 사용자 — GPT가 두 Tool을 한 번에 호출하면 1라운드에 완료된다")
        void loginUser_bothToolsInOneRound_completesInOneLoop() throws Exception {
            when(gptService.callChatCompletion(any())).thenReturn(bothToolsResponse());
            when(hiraApiService.searchHospitals(any())).thenReturn(hospitalResponse("H001"));

            HospitalAssistantResponse result = agentService.run(
                    buildRequest("목이 아프고 열이 납니다", 2), loginProfile());

            assertThat(result.getDepartmentCode()).isEqualTo("12");
            assertThat(result.getDepartmentName()).isEqualTo("이비인후과");
            assertThat(result.getIcd10Code()).isEqualTo("J06.9");
            assertThat(result.getHospitals().getHospitals()).isNotEmpty();

            verify(gptService, times(1)).callChatCompletion(any());
        }

        @Test
        @DisplayName("게스트 사용자 — UserProfileDto.guestDefault() 로 동일하게 완료된다")
        void guestUser_bothToolsInOneRound_completesNormally() throws Exception {
            when(gptService.callChatCompletion(any())).thenReturn(bothToolsResponse());
            when(hiraApiService.searchHospitals(any())).thenReturn(hospitalResponse("H001"));

            HospitalAssistantResponse result = agentService.run(
                    buildRequest("I have a sore throat", 1), UserProfileDto.guestDefault());

            assertThat(result.getDepartmentCode()).isNotBlank();
            assertThat(result.getIcd10Code()).isEqualTo("J06.9");
        }

        @Test
        @DisplayName("GPT가 Tool을 순차적으로 호출하면 2라운드에 완료된다")
        void toolsCalledSequentially_completesInTwoRounds() throws Exception {
            when(gptService.callChatCompletion(any()))
                    .thenReturn(searchOnlyResponse())   // 1라운드: search_hospitals
                    .thenReturn(icd10OnlyResponse());   // 2라운드: extract_icd10_code

            when(hiraApiService.searchHospitals(any())).thenReturn(hospitalResponse("H001"));

            HospitalAssistantResponse result = agentService.run(
                    buildRequest("복통이 심합니다", 3), loginProfile());

            assertThat(result.getDepartmentCode()).isEqualTo("01");
            assertThat(result.getIcd10Code()).isEqualTo("J06.9");

            verify(gptService, times(2)).callChatCompletion(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. 위험도별 clCd 범위 및 반경 검증
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("위험도별 clCd 범위 · 반경 매핑")
    class RiskLevelMapping {

        @Test
        @DisplayName("위험도 1 — HIRA 4종 (31·21·11·01) 반경 3km 호출")
        void riskLevel1_callsFourClCds_radius3() throws Exception {
            when(gptService.callChatCompletion(any())).thenReturn(bothToolsResponse());
            when(hiraApiService.searchHospitals(any())).thenReturn(hospitalResponse("H001"));

            agentService.run(buildRequest("두통", 1), loginProfile());

            ArgumentCaptor<HospitalSearchRequest> captor =
                    ArgumentCaptor.forClass(HospitalSearchRequest.class);
            verify(hiraApiService, times(4)).searchHospitals(captor.capture());

            List<String> clCds = captor.getAllValues().stream()
                    .map(HospitalSearchRequest::getClCd).toList();
            assertThat(clCds).containsExactlyInAnyOrder("31", "21", "11", "01");

            assertThat(captor.getAllValues()).allMatch(r -> r.getRadius() == 3000);
        }

        @Test
        @DisplayName("위험도 3 — HIRA 3종 (21·11·01) 반경 15km 호출")
        void riskLevel3_callsThreeClCds_radius15() throws Exception {
            when(gptService.callChatCompletion(any())).thenReturn(bothToolsResponse());
            when(hiraApiService.searchHospitals(any())).thenReturn(hospitalResponse("H001"));

            agentService.run(buildRequest("복통", 3), loginProfile());

            ArgumentCaptor<HospitalSearchRequest> captor =
                    ArgumentCaptor.forClass(HospitalSearchRequest.class);
            verify(hiraApiService, times(3)).searchHospitals(captor.capture());

            assertThat(captor.getAllValues().stream().map(HospitalSearchRequest::getClCd).toList())
                    .containsExactlyInAnyOrder("21", "11", "01");
            assertThat(captor.getAllValues()).allMatch(r -> r.getRadius() == 15000);
        }

        @Test
        @DisplayName("위험도 4 — HIRA 2종 (11·01) 반경 15km 호출")
        void riskLevel4_callsTwoClCds_radius15() throws Exception {
            when(gptService.callChatCompletion(any())).thenReturn(bothToolsResponse());
            when(hiraApiService.searchHospitals(any())).thenReturn(hospitalResponse("H001"));

            agentService.run(buildRequest("흉통", 4), loginProfile());

            ArgumentCaptor<HospitalSearchRequest> captor =
                    ArgumentCaptor.forClass(HospitalSearchRequest.class);
            verify(hiraApiService, times(2)).searchHospitals(captor.capture());

            assertThat(captor.getAllValues().stream().map(HospitalSearchRequest::getClCd).toList())
                    .containsExactlyInAnyOrder("11", "01");
            assertThat(captor.getAllValues()).allMatch(r -> r.getRadius() == 15000);
        }

        @Test
        @DisplayName("위험도 5 — HIRA 1종 (01) 반경 15km 호출")
        void riskLevel5_callsOneClCd_radius15() throws Exception {
            when(gptService.callChatCompletion(any())).thenReturn(bothToolsResponse());
            when(hiraApiService.searchHospitals(any())).thenReturn(hospitalResponse("H001"));

            agentService.run(buildRequest("의식 저하", 5), loginProfile());

            ArgumentCaptor<HospitalSearchRequest> captor =
                    ArgumentCaptor.forClass(HospitalSearchRequest.class);
            verify(hiraApiService, times(1)).searchHospitals(captor.capture());

            assertThat(captor.getValue().getClCd()).isEqualTo("01");
            assertThat(captor.getValue().getRadius()).isEqualTo(15000);
        }

        @Test
        @DisplayName("GPS 좌표가 HIRA 요청의 XPos·YPos에 정확히 전달된다")
        void gpsCoordinates_passedCorrectlyToHira() throws Exception {
            when(gptService.callChatCompletion(any())).thenReturn(bothToolsResponse());
            when(hiraApiService.searchHospitals(any())).thenReturn(hospitalResponse("H001"));

            agentService.run(buildRequest("발열", 5), loginProfile());

            ArgumentCaptor<HospitalSearchRequest> captor =
                    ArgumentCaptor.forClass(HospitalSearchRequest.class);
            verify(hiraApiService).searchHospitals(captor.capture());

            assertThat(captor.getValue().getXPos()).isEqualTo(126.9780);
            assertThat(captor.getValue().getYPos()).isEqualTo(37.5665);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. 다중 HIRA 호출 결과 병합 및 중복 제거
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("HIRA 다중 호출 병합 · 중복 제거")
    class MergeAndDedup {

        @Test
        @DisplayName("ykiho 중복 병원은 한 번만 포함된다")
        void duplicateYkiho_deduplicatedInResult() throws Exception {
            when(gptService.callChatCompletion(any())).thenReturn(bothToolsResponse());
            // riskLevel=3 → clCd: [21, 11, 01]
            when(hiraApiService.searchHospitals(any())).thenAnswer(inv -> {
                String clCd = ((HospitalSearchRequest) inv.getArgument(0)).getClCd();
                return switch (clCd) {
                    case "21" -> hospitalResponse("H001", "H002");
                    case "11" -> hospitalResponse("H001", "H003"); // H001 중복
                    case "01" -> hospitalResponse("H004");
                    default   -> hospitalResponse();
                };
            });

            HospitalAssistantResponse result = agentService.run(
                    buildRequest("복통", 3), loginProfile());

            List<HospitalDto> hospitals = result.getHospitals().getHospitals();
            long uniqueYkihoCount = hospitals.stream()
                    .map(HospitalDto::getYkiho)
                    .distinct().count();

            assertThat(hospitals).hasSize(4);             // H001, H002, H003, H004
            assertThat(uniqueYkihoCount).isEqualTo(4);    // 중복 없음
        }

        @Test
        @DisplayName("특정 clCd에서 HIRA 오류 발생 시 나머지 결과는 정상 반환된다")
        void hiraFailsForOneClCd_otherResultsStillReturned() throws Exception {
            when(gptService.callChatCompletion(any())).thenReturn(bothToolsResponse());
            // riskLevel=3 → clCd: [21, 11, 01]
            when(hiraApiService.searchHospitals(any())).thenAnswer(inv -> {
                String clCd = ((HospitalSearchRequest) inv.getArgument(0)).getClCd();
                return switch (clCd) {
                    case "21" -> hospitalResponse("H001");
                    case "11" -> throw new ExternalApiException("HIRA_API_ERROR", "서비스 불가");
                    case "01" -> hospitalResponse("H002");
                    default   -> hospitalResponse();
                };
            });

            HospitalAssistantResponse result = agentService.run(
                    buildRequest("복통", 3), loginProfile());

            assertThat(result.getHospitals().getHospitals())
                    .extracting(HospitalDto::getYkiho)
                    .containsExactlyInAnyOrder("H001", "H002");
        }

        @Test
        @DisplayName("모든 clCd에서 HIRA 오류 발생 시에도 빈 병원 목록으로 응답이 반환된다")
        void allHiraCallsFail_returnsEmptyHospitals() throws Exception {
            when(gptService.callChatCompletion(any())).thenReturn(bothToolsResponse());
            when(hiraApiService.searchHospitals(any()))
                    .thenThrow(new ExternalApiException("HIRA_API_ERROR", "서비스 불가"));

            HospitalAssistantResponse result = agentService.run(
                    buildRequest("두통", 5), loginProfile());

            assertThat(result.getHospitals().getHospitals()).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 4. 예외 처리
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("예외 처리")
    class ExceptionHandling {

        @Test
        @DisplayName("최대 반복 후에도 병원을 탐색하지 못하면 AGENT_INCOMPLETE 예외가 발생한다")
        void maxIterationsWithNoHospitals_throwsAgentIncomplete() throws Exception {
            // GPT가 계속 ICD-10만 호출 → hospitals는 끝까지 null
            when(gptService.callChatCompletion(any())).thenReturn(icd10OnlyResponse());

            assertThatThrownBy(() -> agentService.run(
                    buildRequest("두통", 2), loginProfile()))
                    .isInstanceOf(ExternalApiException.class)
                    .hasMessageContaining("병원 검색을 완료하지 못했습니다");
        }

        @Test
        @DisplayName("GPT API 호출 실패 시 ExternalApiException이 전파된다")
        void gptApiFails_exceptionPropagates() {
            when(gptService.callChatCompletion(any()))
                    .thenThrow(new ExternalApiException("GPT_API_ERROR", "GPT 서비스 불가"));

            assertThatThrownBy(() -> agentService.run(
                    buildRequest("발열", 1), loginProfile()))
                    .isInstanceOf(ExternalApiException.class)
                    .hasMessageContaining("GPT 서비스 불가");
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 5. 게스트 프로필 검증
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("게스트 프로필")
    class GuestProfile {

        @Test
        @DisplayName("UserProfileDto.guestDefault()는 age=0, gender=null, insured=false를 반환한다")
        void guestDefault_hasCorrectValues() {
            UserProfileDto guest = UserProfileDto.guestDefault();

            assertThat(guest.getAge()).isZero();
            assertThat(guest.getGender()).isNull();
            assertThat(guest.isInsured()).isFalse();
            assertThat(guest.isGuest()).isTrue();
        }

        @Test
        @DisplayName("로그인 프로필은 isGuest()가 false를 반환한다")
        void loginProfile_isGuestReturnsFalse() {
            assertThat(loginProfile().isGuest()).isFalse();
        }
    }
}
