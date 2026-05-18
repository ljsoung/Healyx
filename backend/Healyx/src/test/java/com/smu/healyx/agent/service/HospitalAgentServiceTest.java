package com.smu.healyx.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smu.healyx.agent.dto.HospitalAssistantRequest;
import com.smu.healyx.agent.dto.HospitalAssistantResponse;
import com.smu.healyx.gpt.dto.GptChatResponse;
import com.smu.healyx.gpt.dto.GptToolCall;
import com.smu.healyx.gpt.service.GptService;
import com.smu.healyx.hira.dto.HospitalDto;
import com.smu.healyx.hira.dto.HospitalSearchRequest;
import com.smu.healyx.hira.dto.HospitalSearchResponse;
import com.smu.healyx.hira.service.HiraApiService;
import com.smu.healyx.hospital.repository.ForeignCertifiedHospitalRepository;
import com.smu.healyx.user.dto.UserProfileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * HospitalAgentService 단위 테스트 — 이슈 #62, #63 회귀 방지
 *
 * 외부 의존성(GptService, HiraApiService, ForeignCertifiedHospitalRepository)은
 * Mockito Mock으로 격리하고 searchAcrossHospitalTypes 로직을 직접 검증합니다.
 *
 * LENIENT strictness: 빈 결과 케이스(TC-3,4,5)에서는 merged가 비어있어
 * foreignCertifiedHospitalRepository 호출이 발생하지 않으므로 lenient로 설정합니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HospitalAgentService 단위 테스트 (이슈 #62, #63)")
class HospitalAgentServiceTest {

    @Mock
    private GptService gptService;

    @Mock
    private HiraApiService hiraApiService;

    @Mock
    private ForeignCertifiedHospitalRepository foreignCertifiedHospitalRepository;

    private HospitalAgentService hospitalAgentService;
    private ObjectMapper objectMapper;

    private UserProfileDto guestProfile;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        hospitalAgentService = new HospitalAgentService(
                gptService, hiraApiService, objectMapper, foreignCertifiedHospitalRepository);
        guestProfile = UserProfileDto.guestDefault();

        // ForeignCertifiedHospitalRepository: 항상 빈 리스트 반환 (외국인 인증 여부는 이 테스트 범위 아님)
        when(foreignCertifiedHospitalRepository.findAllByYkihoIn(anyCollection()))
                .thenReturn(List.of());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-1: dgsbjtCd 후처리 필터 — 일치하는 병원만 남김 (#62)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-1: dgsbjtCd=11(안과) 필터 — 안과 병원만 남고 산부인과(09) 병원은 제거됨 (#62)")
    void searchAcrossHospitalTypes_dgsbjtCdFilter_keepsMatchingHospitalsOnly() {
        // given — GPT가 search_hospitals(dgsbjtCd="11") + extract_icd10 두 tool call 반환
        setupGptMockWithTwoCalls("11", "안과", "H10.1");

        // HIRA API: 안과 병원 1건 + 산부인과 병원 1건 반환
        HospitalDto eyeHospital = buildHospital("ykiho-eye", "안과병원", "11");
        HospitalDto obgynHospital = buildHospital("ykiho-obgyn", "산부인과병원", "09");
        HospitalSearchResponse hiraResponse = buildHiraResponse(eyeHospital, obgynHospital);

        // riskLevel=1 → clCd=[31,21,11,01] 4번 호출 — 모두 같은 응답 반환
        when(hiraApiService.searchHospitals(any(HospitalSearchRequest.class)))
                .thenReturn(hiraResponse);

        HospitalAssistantRequest req = buildRequest("눈이 충혈됐어요", 1);

        // when
        HospitalAssistantResponse response = hospitalAgentService.run(req, guestProfile);

        // then — 안과 병원만 포함되어야 함
        List<HospitalDto> hospitals = response.getHospitals().getHospitals();
        assertThat(hospitals)
                .as("dgsbjtCd=11 필터 후 안과 병원만 남아야 함")
                .extracting(HospitalDto::getYkiho)
                .containsExactly("ykiho-eye");
        assertThat(hospitals)
                .as("산부인과 병원(dgsbjtCd=09)은 제거되어야 함")
                .extracting(HospitalDto::getYkiho)
                .doesNotContain("ykiho-obgyn");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-2: dgsbjtCd=null 병원은 필터 통과 (포용적 정책) (#62)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-2: dgsbjtCd=null 병원은 필터 통과, dgsbjtCd 불일치 병원은 제거됨 (#62 포용적 정책)")
    void searchAcrossHospitalTypes_nullDgsbjtCdHospital_passesFilter() {
        // given — GPT: dgsbjtCd="11" 요청
        setupGptMockWithTwoCalls("11", "안과", "H10.1");

        // HIRA API: dgsbjtCd=null 병원 + dgsbjtCd="09" 병원 반환
        HospitalDto nullDeptHospital = buildHospital("ykiho-null", "일반병원", null);
        HospitalDto obgynHospital    = buildHospital("ykiho-obgyn", "산부인과병원", "09");
        HospitalSearchResponse hiraResponse = buildHiraResponse(nullDeptHospital, obgynHospital);

        when(hiraApiService.searchHospitals(any(HospitalSearchRequest.class)))
                .thenReturn(hiraResponse);

        HospitalAssistantRequest req = buildRequest("눈이 아파요", 1);

        // when
        HospitalAssistantResponse response = hospitalAgentService.run(req, guestProfile);

        // then — null 병원은 포함, 산부인과는 제거
        List<HospitalDto> hospitals = response.getHospitals().getHospitals();
        assertThat(hospitals)
                .as("dgsbjtCd=null 병원은 포용적 정책에 따라 필터를 통과해야 함")
                .extracting(HospitalDto::getYkiho)
                .contains("ykiho-null");
        assertThat(hospitals)
                .as("dgsbjtCd=09 병원은 요청 코드 11과 불일치하므로 제거되어야 함")
                .extracting(HospitalDto::getYkiho)
                .doesNotContain("ykiho-obgyn");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-3: riskLevel=3 → 반경 10000m (#63 수정 검증)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-3: riskLevel=3 요청 시 HIRA API 반경이 10000m이어야 함 (#63 수정 — 기존 15000 → 10000)")
    void searchAcrossHospitalTypes_riskLevel3_usesRadius10000() {
        // given
        setupGptMockWithTwoCalls("01", "내과", "J06.9");

        when(hiraApiService.searchHospitals(any(HospitalSearchRequest.class)))
                .thenReturn(emptyHiraResponse());

        HospitalAssistantRequest req = buildRequest("배가 아파요", 3);

        // when
        hospitalAgentService.run(req, guestProfile);

        // then — 모든 HIRA 호출에서 radius=10000 이어야 함
        ArgumentCaptor<HospitalSearchRequest> captor =
                ArgumentCaptor.forClass(HospitalSearchRequest.class);
        org.mockito.Mockito.verify(hiraApiService, org.mockito.Mockito.atLeastOnce())
                .searchHospitals(captor.capture());

        captor.getAllValues().forEach(searchReq ->
                assertThat(searchReq.getRadius())
                        .as("riskLevel=3의 반경은 10000m이어야 함 (기존 버그: 15000)")
                        .isEqualTo(10000));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-4: merged 빈 리스트 — 500 없이 빈 응답 반환 (#63)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-4: 모든 HIRA 호출이 빈 결과일 때 500 예외 없이 빈 리스트 응답 반환 (#63)")
    void searchAcrossHospitalTypes_emptyHiraResult_returnsEmptyListWithoutException() {
        // given — 모든 clCd에 대해 빈 리스트 반환
        setupGptMockWithTwoCalls("12", "이비인후과", "J30.1");

        when(hiraApiService.searchHospitals(any(HospitalSearchRequest.class)))
                .thenReturn(emptyHiraResponse());

        HospitalAssistantRequest req = buildRequest("코가 막혀요", 1);

        // when & then — 예외 없이 정상 실행
        assertThatNoException().isThrownBy(() -> {
            HospitalAssistantResponse response = hospitalAgentService.run(req, guestProfile);

            // 빈 리스트 응답 검증
            assertThat(response.getHospitals().getHospitals())
                    .as("결과 없을 때 빈 리스트를 반환해야 함")
                    .isEmpty();
            assertThat(response.getHospitals().getTotalCount())
                    .as("totalCount는 0이어야 함")
                    .isEqualTo(0);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-5: riskLevel=5 → 반경 15000m (#63 — 5단계는 그대로 유지)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-5: riskLevel=5 요청 시 HIRA API 반경이 15000m이어야 함 (#63 — 5단계 유지 확인)")
    void searchAcrossHospitalTypes_riskLevel5_usesRadius15000() {
        // given — riskLevel=5: clCd=["01"] 1번만 호출
        setupGptMockWithTwoCalls("21", "응급의학과", "T07");

        when(hiraApiService.searchHospitals(any(HospitalSearchRequest.class)))
                .thenReturn(emptyHiraResponse());

        HospitalAssistantRequest req = buildRequest("심한 가슴 통증", 5);

        // when
        hospitalAgentService.run(req, guestProfile);

        // then
        ArgumentCaptor<HospitalSearchRequest> captor =
                ArgumentCaptor.forClass(HospitalSearchRequest.class);
        org.mockito.Mockito.verify(hiraApiService, org.mockito.Mockito.atLeastOnce())
                .searchHospitals(captor.capture());

        captor.getAllValues().forEach(searchReq ->
                assertThat(searchReq.getRadius())
                        .as("riskLevel=5의 반경은 15000m이어야 함")
                        .isEqualTo(15000));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 헬퍼 메서드
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GPT가 두 번 호출되는 상황을 Mock으로 설정합니다.
     *   1회차: tool_calls=[search_hospitals(dgsbjtCd, departmentName), extract_icd10(icd10Code)]
     *   2회차: finish_reason="stop"
     */
    private void setupGptMockWithTwoCalls(String dgsbjtCd, String departmentName, String icd10Code) {
        // search_hospitals tool call
        GptToolCall searchCall = makeToolCall(
                "call-search-001",
                "search_hospitals",
                String.format("{\"dgsbjtCd\":\"%s\",\"departmentName\":\"%s\"}", dgsbjtCd, departmentName));

        // extract_icd10_code tool call
        GptToolCall icd10Call = makeToolCall(
                "call-icd10-002",
                "extract_icd10_code",
                String.format("{\"icd10Code\":\"%s\"}", icd10Code));

        // 1회차: tool_calls 반환
        GptChatResponse firstResponse = makeToolCallsResponse(List.of(searchCall, icd10Call));

        // 2회차: stop 반환 (Loop 종료)
        GptChatResponse stopResponse = makeStopResponse();

        when(gptService.callChatCompletion(any()))
                .thenReturn(firstResponse)
                .thenReturn(stopResponse);
    }

    /**
     * GptToolCall 인스턴스를 ReflectionTestUtils로 생성합니다.
     * GptToolCall은 @NoArgsConstructor만 제공하므로 리플렉션으로 필드를 설정합니다.
     */
    private GptToolCall makeToolCall(String id, String functionName, String arguments) {
        GptToolCall toolCall = new GptToolCall();
        ReflectionTestUtils.setField(toolCall, "id", id);
        ReflectionTestUtils.setField(toolCall, "type", "function");

        GptToolCall.FunctionCall functionCall = new GptToolCall.FunctionCall();
        ReflectionTestUtils.setField(functionCall, "name", functionName);
        ReflectionTestUtils.setField(functionCall, "arguments", arguments);

        ReflectionTestUtils.setField(toolCall, "function", functionCall);
        return toolCall;
    }

    /**
     * finish_reason="tool_calls" 인 GptChatResponse를 생성합니다.
     */
    private GptChatResponse makeToolCallsResponse(List<GptToolCall> toolCalls) {
        GptChatResponse response = new GptChatResponse();

        GptChatResponse.Message message = new GptChatResponse.Message();
        ReflectionTestUtils.setField(message, "role", "assistant");
        ReflectionTestUtils.setField(message, "content", null);
        ReflectionTestUtils.setField(message, "toolCalls", toolCalls);

        GptChatResponse.Choice choice = new GptChatResponse.Choice();
        ReflectionTestUtils.setField(choice, "message", message);
        ReflectionTestUtils.setField(choice, "finishReason", "tool_calls");

        ReflectionTestUtils.setField(response, "choices", List.of(choice));
        return response;
    }

    /**
     * finish_reason="stop" 인 GptChatResponse를 생성합니다.
     */
    private GptChatResponse makeStopResponse() {
        GptChatResponse response = new GptChatResponse();

        GptChatResponse.Message message = new GptChatResponse.Message();
        ReflectionTestUtils.setField(message, "role", "assistant");
        ReflectionTestUtils.setField(message, "content", "처리 완료되었습니다.");
        ReflectionTestUtils.setField(message, "toolCalls", null);

        GptChatResponse.Choice choice = new GptChatResponse.Choice();
        ReflectionTestUtils.setField(choice, "message", message);
        ReflectionTestUtils.setField(choice, "finishReason", "stop");

        ReflectionTestUtils.setField(response, "choices", List.of(choice));
        return response;
    }

    private HospitalDto buildHospital(String ykiho, String name, String dgsbjtCd) {
        return HospitalDto.builder()
                .ykiho(ykiho)
                .hospitalName(name)
                .address("서울시 강남구")
                .telephone("02-0000-0000")
                .longitude(127.0276)
                .latitude(37.4979)
                .distance(500)
                .clCd("31")
                .hospitalType("의원")
                .sidoCd("110000")
                .sidoCdNm("서울")
                .dgsbjtCd(dgsbjtCd)
                .foreignCertified(false)
                .build();
    }

    private HospitalSearchResponse buildHiraResponse(HospitalDto... hospitals) {
        return HospitalSearchResponse.builder()
                .hospitals(List.of(hospitals))
                .pageNo(1)
                .numOfRows(hospitals.length)
                .totalCount(hospitals.length)
                .build();
    }

    private HospitalSearchResponse emptyHiraResponse() {
        return HospitalSearchResponse.builder()
                .hospitals(List.of())
                .pageNo(1)
                .numOfRows(0)
                .totalCount(0)
                .build();
    }

    private HospitalAssistantRequest buildRequest(String symptom, int riskLevel) {
        return HospitalAssistantRequest.of(symptom, riskLevel, 37.4979, 127.0276);
    }
}
