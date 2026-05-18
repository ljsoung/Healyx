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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HospitalAgentService 단위 테스트 — 2단계 검색 방식 검증
 *
 * 2단계 검색 로직:
 *   1단계: GPS → sidoCd 변환 후 dgsbjtCd + sidoCd + clCd 로 HIRA 전국 조회 (xPos/yPos 없음)
 *   2단계: Haversine 공식으로 반경 내 병원만 필터링, GPS=0 병원 제외
 *
 * 외부 의존성(GptService, HiraApiService, ForeignCertifiedHospitalRepository)은
 * Mockito Mock으로 격리합니다.
 *
 * LENIENT strictness: TC-5(빈 결과) 등에서 filtered가 비어있어
 * foreignCertifiedHospitalRepository 호출이 발생하지 않는 케이스 허용.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HospitalAgentService 단위 테스트 (2단계 검색 방식)")
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

    // 서울 좌표 (SidoCodeResolver → "110000")
    private static final double SEOUL_LAT = 37.5665;
    private static final double SEOUL_LON = 126.9780;

    // 반경 내 좌표 (서울 중심에서 약 400m — riskLevel=1의 3000m 안쪽)
    private static final double NEAR_LAT = 37.5700;
    private static final double NEAR_LON = 126.9800;

    // 반경 외 좌표 (서울 중심에서 약 15km — riskLevel=1의 3000m 바깥)
    private static final double FAR_LAT = 37.6500;
    private static final double FAR_LON = 127.1000;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        hospitalAgentService = new HospitalAgentService(
                gptService, hiraApiService, objectMapper, foreignCertifiedHospitalRepository);
        guestProfile = UserProfileDto.guestDefault();

        // ForeignCertifiedHospitalRepository: 기본으로 빈 리스트 반환
        when(foreignCertifiedHospitalRepository.findAllByYkihoIn(anyCollection()))
                .thenReturn(List.of());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-1: 진료과 코드 기반 HIRA 호출 검증
    //       - request에 xPos/yPos 없음 (0.0)
    //       - sidoCd = "110000" (서울)
    //       - numOfRows = 100
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-1: HIRA 호출 시 sidoCd=110000, xPos/yPos=0.0, numOfRows=100 검증")
    void searchHospitals_hiraRequest_hasSidoCdAndNoGpsParams() {
        // given
        setupGptMock("11", "안과", "H10.1");

        HospitalDto nearHospital = buildHospital("ykiho-001", "안과의원", "11", NEAR_LAT, NEAR_LON);
        HospitalSearchResponse hiraResp = buildHiraResponse(1, nearHospital);

        when(hiraApiService.searchHospitals(any(HospitalSearchRequest.class)))
                .thenReturn(hiraResp);

        HospitalAssistantRequest req = buildRequest("눈이 충혈됐어요", 1, SEOUL_LAT, SEOUL_LON);

        // when
        hospitalAgentService.run(req, guestProfile);

        // then — HIRA 요청 파라미터 검증
        ArgumentCaptor<HospitalSearchRequest> captor =
                ArgumentCaptor.forClass(HospitalSearchRequest.class);
        verify(hiraApiService, atLeastOnce()).searchHospitals(captor.capture());

        HospitalSearchRequest captured = captor.getAllValues().get(0);
        assertThat(captured.getDgsbjtCd())
                .as("dgsbjtCd는 GPT가 반환한 '11' 이어야 함")
                .isEqualTo("11");
        assertThat(captured.getSidoCd())
                .as("서울 좌표 → sidoCd=110000 이어야 함")
                .isEqualTo("110000");
        assertThat(captured.getXPos())
                .as("2단계 방식: xPos는 0.0 (위치 파라미터 없음)")
                .isEqualTo(0.0);
        assertThat(captured.getYPos())
                .as("2단계 방식: yPos는 0.0 (위치 파라미터 없음)")
                .isEqualTo(0.0);
        assertThat(captured.getNumOfRows())
                .as("numOfRows는 100이어야 함")
                .isEqualTo(100);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-2: Haversine 반경 필터링 — 반경 내 병원만 포함
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-2: Haversine 필터 — 반경 3000m 내 병원만 포함, 반경 외 병원 제외")
    void searchHospitals_haversineFilter_keepsOnlyNearbyHospitals() {
        // given
        setupGptMock("11", "안과", "H10.1");

        HospitalDto nearHospital = buildHospital("ykiho-near", "근처안과", "11", NEAR_LAT, NEAR_LON);
        HospitalDto farHospital  = buildHospital("ykiho-far",  "먼안과",   "11", FAR_LAT,  FAR_LON);
        HospitalSearchResponse hiraResp = buildHiraResponse(2, nearHospital, farHospital);

        when(hiraApiService.searchHospitals(any(HospitalSearchRequest.class)))
                .thenReturn(hiraResp);

        HospitalAssistantRequest req = buildRequest("눈이 아파요", 1, SEOUL_LAT, SEOUL_LON);

        // when
        HospitalAssistantResponse response = hospitalAgentService.run(req, guestProfile);

        // then
        List<HospitalDto> hospitals = response.getHospitals().getHospitals();
        assertThat(hospitals)
                .as("반경 내 병원(ykiho-near)만 포함되어야 함")
                .extracting(HospitalDto::getYkiho)
                .contains("ykiho-near");
        assertThat(hospitals)
                .as("반경 외 병원(ykiho-far)은 제외되어야 함")
                .extracting(HospitalDto::getYkiho)
                .doesNotContain("ykiho-far");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-3: GPS 없는 병원 제외 (lat=0.0, lon=0.0)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-3: GPS 없는 병원(lat=0.0, lon=0.0)은 응답에서 제외됨")
    void searchHospitals_noGpsHospital_isExcluded() {
        // given
        setupGptMock("11", "안과", "H10.1");

        HospitalDto validHospital  = buildHospital("ykiho-valid", "정상안과", "11", NEAR_LAT, NEAR_LON);
        HospitalDto noGpsHospital  = buildHospital("ykiho-nogps", "GPS없는병원", "11", 0.0, 0.0);
        HospitalSearchResponse hiraResp = buildHiraResponse(2, validHospital, noGpsHospital);

        when(hiraApiService.searchHospitals(any(HospitalSearchRequest.class)))
                .thenReturn(hiraResp);

        HospitalAssistantRequest req = buildRequest("눈이 충혈됐어요", 1, SEOUL_LAT, SEOUL_LON);

        // when
        HospitalAssistantResponse response = hospitalAgentService.run(req, guestProfile);

        // then
        List<HospitalDto> hospitals = response.getHospitals().getHospitals();
        assertThat(hospitals)
                .as("GPS 없는 병원(lat=0.0, lon=0.0)은 제외되어야 함")
                .extracting(HospitalDto::getYkiho)
                .doesNotContain("ykiho-nogps");
        assertThat(hospitals)
                .as("GPS 있는 정상 병원은 포함되어야 함")
                .extracting(HospitalDto::getYkiho)
                .contains("ykiho-valid");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-4: 페이지네이션 — totalCount > 100 시 2페이지 호출
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-4: totalCount=200 일 때 pageNo=1, pageNo=2 각각 호출됨")
    void searchHospitals_pagination_callsPage1AndPage2() {
        // given
        setupGptMock("11", "안과", "H10.1");

        // 1페이지: totalCount=200 (2페이지 필요)
        HospitalDto hospital1 = buildHospital("ykiho-p1", "1페이지병원", "11", NEAR_LAT, NEAR_LON);
        HospitalSearchResponse page1Resp = HospitalSearchResponse.builder()
                .hospitals(List.of(hospital1))
                .pageNo(1)
                .numOfRows(100)
                .totalCount(200)
                .build();

        // 2페이지: totalCount=200 (이 페이지로 종료)
        HospitalDto hospital2 = buildHospital("ykiho-p2", "2페이지병원", "11", NEAR_LAT, NEAR_LON);
        HospitalSearchResponse page2Resp = HospitalSearchResponse.builder()
                .hospitals(List.of(hospital2))
                .pageNo(2)
                .numOfRows(100)
                .totalCount(200)
                .build();

        when(hiraApiService.searchHospitals(any(HospitalSearchRequest.class)))
                .thenReturn(page1Resp)  // 첫 번째 clCd, 1페이지
                .thenReturn(page2Resp)  // 첫 번째 clCd, 2페이지
                .thenReturn(emptyHiraResponse()); // 나머지 clCd들

        HospitalAssistantRequest req = buildRequest("눈이 아파요", 1, SEOUL_LAT, SEOUL_LON);

        // when
        hospitalAgentService.run(req, guestProfile);

        // then — pageNo=1, pageNo=2 각각 호출되었는지 확인
        ArgumentCaptor<HospitalSearchRequest> captor =
                ArgumentCaptor.forClass(HospitalSearchRequest.class);
        verify(hiraApiService, atLeastOnce()).searchHospitals(captor.capture());

        List<Integer> pageNos = captor.getAllValues().stream()
                .map(HospitalSearchRequest::getPageNo)
                .toList();
        assertThat(pageNos)
                .as("페이지네이션: pageNo=1, pageNo=2 모두 호출되어야 함")
                .contains(1, 2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-5: empty guard — 반경 내 병원 0건 시 빈 목록 반환 (예외 없음)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-5: HIRA가 반경 외 병원만 반환할 때 빈 목록 반환, 예외 없음")
    void searchHospitals_allOutsideRadius_returnsEmptyWithoutException() {
        // given
        setupGptMock("11", "안과", "H10.1");

        // 반경 외 병원만 반환 (FAR_LAT, FAR_LON)
        HospitalDto farHospital = buildHospital("ykiho-far", "먼병원", "11", FAR_LAT, FAR_LON);
        HospitalSearchResponse hiraResp = buildHiraResponse(1, farHospital);

        when(hiraApiService.searchHospitals(any(HospitalSearchRequest.class)))
                .thenReturn(hiraResp);

        HospitalAssistantRequest req = buildRequest("눈이 아파요", 1, SEOUL_LAT, SEOUL_LON);

        // when & then — 예외 없이 정상 실행, 빈 목록 반환
        assertThatNoException().isThrownBy(() -> {
            HospitalAssistantResponse response = hospitalAgentService.run(req, guestProfile);
            assertThat(response.getHospitals().getHospitals())
                    .as("반경 외 병원만 있을 때 빈 목록을 반환해야 함")
                    .isEmpty();
            assertThat(response.getHospitals().getTotalCount())
                    .as("totalCount는 0이어야 함")
                    .isEqualTo(0);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 헬퍼 메서드
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GPT가 두 tool call(search_hospitals + extract_icd10)을 한 번에 반환하는 Mock 설정.
     *   1회차: tool_calls 반환
     *   2회차: stop 반환
     */
    private void setupGptMock(String dgsbjtCd, String departmentName, String icd10Code) {
        GptToolCall searchCall = makeToolCall(
                "call-search-001",
                "search_hospitals",
                String.format("{\"dgsbjtCd\":\"%s\",\"departmentName\":\"%s\"}", dgsbjtCd, departmentName));

        GptToolCall icd10Call = makeToolCall(
                "call-icd10-002",
                "extract_icd10_code",
                String.format("{\"icd10Code\":\"%s\"}", icd10Code));

        GptChatResponse firstResponse = makeToolCallsResponse(List.of(searchCall, icd10Call));
        GptChatResponse stopResponse  = makeStopResponse();

        when(gptService.callChatCompletion(any()))
                .thenReturn(firstResponse)
                .thenReturn(stopResponse);
    }

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

    private HospitalDto buildHospital(String ykiho, String name, String dgsbjtCd,
                                      double lat, double lon) {
        return HospitalDto.builder()
                .ykiho(ykiho)
                .hospitalName(name)
                .address("서울시 중구")
                .telephone("02-0000-0000")
                .longitude(lon)
                .latitude(lat)
                .distance(0)
                .clCd("31")
                .hospitalType("의원")
                .sidoCd("110000")
                .sidoCdNm("서울")
                .dgsbjtCd(dgsbjtCd)
                .foreignCertified(false)
                .build();
    }

    /** totalCount를 명시적으로 지정하는 빌더 */
    private HospitalSearchResponse buildHiraResponse(int totalCount, HospitalDto... hospitals) {
        return HospitalSearchResponse.builder()
                .hospitals(List.of(hospitals))
                .pageNo(1)
                .numOfRows(hospitals.length)
                .totalCount(totalCount)
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

    private HospitalAssistantRequest buildRequest(String symptom, int riskLevel,
                                                   double lat, double lon) {
        return HospitalAssistantRequest.of(symptom, riskLevel, lat, lon);
    }
}
