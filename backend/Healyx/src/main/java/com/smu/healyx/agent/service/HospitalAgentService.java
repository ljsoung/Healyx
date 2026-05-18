package com.smu.healyx.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smu.healyx.agent.dto.HospitalAssistantRequest;
import com.smu.healyx.agent.dto.HospitalAssistantResponse;
import com.smu.healyx.common.exception.ExternalApiException;
import com.smu.healyx.gpt.dto.GptChatRequest;
import com.smu.healyx.gpt.dto.GptChatResponse;
import com.smu.healyx.gpt.dto.GptTool;
import com.smu.healyx.gpt.dto.GptToolCall;
import com.smu.healyx.gpt.service.GptService;
import com.smu.healyx.hira.dto.HospitalDto;
import com.smu.healyx.hira.dto.HospitalSearchRequest;
import com.smu.healyx.hira.dto.HospitalSearchResponse;
import com.smu.healyx.hira.service.HiraDgsbjtCacheService;
import com.smu.healyx.hira.service.HiraApiService;
import com.smu.healyx.hira.util.DepartmentNameMatcher;
import com.smu.healyx.hira.util.HaversineUtils;
import com.smu.healyx.hira.util.SidoCodeResolver;
import com.smu.healyx.hospital.domain.ForeignCertifiedHospital;
import com.smu.healyx.hospital.repository.ForeignCertifiedHospitalRepository;
import com.smu.healyx.user.dto.UserProfileDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * OpenAI Function Calling 기반 병원 탐색 AI Agent.
 *
 * 역할 분담:
 *   GPT    → 증상을 분석하여 HIRA 진료과목 코드(dgsbjtCd) 결정
 *   서버   → 위험도(1-5)를 병원 종별 범위(clCd 목록)·반경으로 변환,
 *            clCd별 HIRA API 다중 호출 후 병합·중복 제거
 *
 * Agent Loop:
 *   1. GPT → search_hospitals(dgsbjtCd) + extract_icd10_code 호출
 *   2. 서버 → 각 Tool 실행 → 결과를 대화 히스토리에 추가
 *   3. 두 Tool 완료 시 Loop 종료 → 통합 응답 반환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HospitalAgentService {

    private final GptService gptService;
    private final HiraApiService hiraApiService;
    private final HiraDgsbjtCacheService hiraDgsbjtCacheService;
    private final ObjectMapper objectMapper;
    private final ForeignCertifiedHospitalRepository foreignCertifiedHospitalRepository;
    // 빈 이름 "dgsbjtVerifyExecutor"와 필드명 일치 → Spring @Primary 없이 단독 Executor 빈으로 주입됨
    private final Executor dgsbjtVerifyExecutor;

    private static final String AGENT_MODEL = "gpt-4o";
    private static final int MAX_ITERATIONS = 6;

    private static final String TOOL_SEARCH_HOSPITALS = "search_hospitals";
    private static final String TOOL_EXTRACT_ICD10    = "extract_icd10_code";

    /**
     * 위험도별 병원 종별 범위 (낮은 단계일수록 더 많은 종별 포함)
     *
     *   1: 의원 ~ 상급종합 [31, 21, 11, 01]
     *   2: 의원 ~ 상급종합 [31, 21, 11, 01]
     *   3: 병원 ~ 상급종합 [21, 11, 01]
     *   4: 종합병원 ~ 상급종합 [11, 01]
     *   5: 상급종합만 [01]
     */
    private static final Map<Integer, List<String>> RISK_TO_CL_CDS = Map.of(
            1, List.of("31", "21", "11", "01"),
            2, List.of("31", "21", "11", "01"),
            3, List.of("21", "11", "01"),
            4, List.of("11", "01"),
            5, List.of("01")
    );

    /** 위험도별 검색 반경 (m): 1~2단계 3km, 3~4단계 10km, 5단계 15km */
    private static final Map<Integer, Integer> RISK_TO_RADIUS = Map.of(
            1, 3000,
            2, 3000,
            3, 10000,
            4, 10000,
            5, 15000
    );

    public HospitalAssistantResponse run(HospitalAssistantRequest req, UserProfileDto userProfile) {
        List<GptChatRequest.Message> messages = buildInitialMessages(req, userProfile);
        List<GptTool> tools = buildTools();

        String departmentCode    = null;
        String departmentName    = null;
        HospitalSearchResponse hospitals = null;
        String icd10Code         = null;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            boolean needMore  = (hospitals == null || icd10Code == null);
            String toolChoice = needMore ? "required" : "auto";

            GptChatRequest chatReq = new GptChatRequest(
                    AGENT_MODEL, messages, 512, 0.0, tools, toolChoice);

            GptChatResponse response = gptService.callChatCompletion(chatReq);
            String finishReason = response.getFinishReason();
            log.debug("Agent 반복 {}: finish_reason={}", i + 1, finishReason);

            if ("stop".equals(finishReason)) break;

            if (!"tool_calls".equals(finishReason)) {
                log.warn("예상치 못한 finish_reason: {}", finishReason);
                break;
            }

            List<GptToolCall> toolCalls = response.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) break;

            messages.add(GptChatRequest.Message.ofAssistantToolCalls(toolCalls));

            for (GptToolCall call : toolCalls) {
                String toolName = call.getFunction().getName();
                String argsJson = call.getFunction().getArguments();

                try {
                    JsonNode args = objectMapper.readTree(argsJson);

                    if (TOOL_SEARCH_HOSPITALS.equals(toolName)) {
                        departmentCode = args.path("dgsbjtCd").asText();
                        departmentName = args.path("departmentName").asText();

                        // clCd 범위·반경은 위험도 기반 서버 로직으로 결정
                        hospitals = searchAcrossHospitalTypes(departmentCode, req);

                        String result = objectMapper.writeValueAsString(
                                Map.of("success", true, "totalCount", hospitals.getTotalCount()));
                        messages.add(GptChatRequest.Message.ofToolResult(call.getId(), result));
                        log.debug("search_hospitals: dgsbjtCd={}, totalCount={}", departmentCode, hospitals.getTotalCount());

                    } else if (TOOL_EXTRACT_ICD10.equals(toolName)) {
                        icd10Code = args.path("icd10Code").asText();

                        messages.add(GptChatRequest.Message.ofToolResult(
                                call.getId(), "{\"recorded\":true}"));
                        log.debug("extract_icd10_code: code={}", icd10Code);
                    }

                } catch (Exception e) {
                    log.error("Tool 실행 실패: tool={}, error={}", toolName, e.getMessage());
                    messages.add(GptChatRequest.Message.ofToolResult(
                            call.getId(), "{\"error\":\"" + e.getMessage() + "\"}"));
                }
            }

            if (hospitals != null && icd10Code != null) {
                log.debug("Agent: 두 Tool 완료 → Loop 종료");
                break;
            }
        }

        if (hospitals == null) {
            throw new ExternalApiException("AGENT_INCOMPLETE", "병원 검색을 완료하지 못했습니다. 다시 시도해 주세요.");
        }

        return HospitalAssistantResponse.builder()
                .departmentCode(departmentCode)
                .departmentName(departmentName)
                .hospitals(hospitals)
                .icd10Code(icd10Code)
                .build();
    }

    // ── 다중 병원 종별 HIRA 호출 + 병합 ───────────────────────────────

    /**
     * 2단계 검색:
     *   1단계 — GPS → sidoCd 변환 후 진료과(dgsbjtCd) + 시도(sidoCd) + 종별(clCd)로 HIRA 전국 조회
     *   2단계 — Haversine 공식으로 반경 내 병원만 필터링, distance 값 갱신
     *
     * 이름 기반 진료과 필터(SPECIALTY_KEYWORDS)는 제거됨.
     * sidoCd 매칭 실패 시 sidoCd 없이 호출(전국 fallback) → Haversine 필터는 그대로 동작.
     */
    private HospitalSearchResponse searchAcrossHospitalTypes(
            String dgsbjtCd, HospitalAssistantRequest req) {

        List<String> clCds = RISK_TO_CL_CDS.getOrDefault(req.getRiskLevel(), List.of("31", "21", "11", "01"));
        int radius         = RISK_TO_RADIUS.getOrDefault(req.getRiskLevel(), 3000);

        // GPS → sidoCd 변환 (경계 지역 등 매칭 실패 시 null → 전국 조회 fallback)
        String sidoCd = SidoCodeResolver.resolve(req.getLatitude(), req.getLongitude());
        if (sidoCd == null) {
            log.warn("sidoCd 매칭 실패 (lat={}, lon={}) → 전국 조회 fallback", req.getLatitude(), req.getLongitude());
        } else {
            log.debug("sidoCd 결정: {} (lat={}, lon={})", sidoCd, req.getLatitude(), req.getLongitude());
        }

        // LinkedHashMap으로 삽입 순서(종별 우선순위) 유지하면서 ykiho 중복 제거
        Map<String, HospitalDto> merged = new LinkedHashMap<>();

        for (String clCd : clCds) {
            int maxPage = 5;
            for (int page = 1; page <= maxPage; page++) {
                try {
                    HospitalSearchRequest searchReq = buildSearchRequest(dgsbjtCd, clCd, sidoCd, page, 100);
                    HospitalSearchResponse result   = hiraApiService.searchHospitals(searchReq);

                    for (HospitalDto hospital : result.getHospitals()) {
                        if (hospital.getYkiho() != null) {
                            merged.putIfAbsent(hospital.getYkiho(), hospital);
                        }
                    }
                    log.debug("HIRA 조회: clCd={}, page={}, 건수={}", clCd, page, result.getTotalCount());

                    // 다음 페이지 없으면 종료
                    if (result.getTotalCount() <= page * 100) break;

                } catch (Exception e) {
                    log.warn("HIRA 호출 실패 (clCd={}, page={}): {}", clCd, page, e.getMessage());
                    break;
                }
            }
        }

        // 모든 clCd 호출 실패 또는 결과 없음 — NPE 및 JPA IN-empty 예외 방지
        if (merged.isEmpty()) {
            log.warn("2단계 검색 결과 없음 (dgsbjtCd={}, sidoCd={})", dgsbjtCd, sidoCd);
            return HospitalSearchResponse.builder()
                    .hospitals(List.of())
                    .pageNo(1)
                    .numOfRows(0)
                    .totalCount(0)
                    .build();
        }

        // Haversine 반경 필터링 + distance 값 갱신
        double userLat = req.getLatitude();
        double userLon = req.getLongitude();

        Map<String, HospitalDto> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, HospitalDto> entry : merged.entrySet()) {
            HospitalDto dto = entry.getValue();
            // GPS 좌표 없는 병원 제외
            if (dto.getLatitude() == 0.0 || dto.getLongitude() == 0.0) continue;

            double distM = HaversineUtils.distanceMeters(
                    userLat, userLon, dto.getLatitude(), dto.getLongitude());

            if (distM <= radius) {
                // distance 필드를 Haversine 계산값(m)으로 교체한 새 DTO 생성
                HospitalDto updated = HospitalDto.builder()
                        .ykiho(dto.getYkiho())
                        .hospitalName(dto.getHospitalName())
                        .address(dto.getAddress())
                        .telephone(dto.getTelephone())
                        .longitude(dto.getLongitude())
                        .latitude(dto.getLatitude())
                        .distance((int) Math.round(distM))
                        .clCd(dto.getClCd())
                        .hospitalType(dto.getHospitalType())
                        .sidoCd(dto.getSidoCd())
                        .sidoCdNm(dto.getSidoCdNm())
                        .dgsbjtCd(dto.getDgsbjtCd())
                        .foreignCertified(false) // 아래 DB 조회 후 갱신
                        .build();
                filtered.put(entry.getKey(), updated);
            }
        }

        // 반경 내 결과 없음
        if (filtered.isEmpty()) {
            log.warn("반경 {}m 내 병원 없음 (dgsbjtCd={}, lat={}, lon={})",
                    radius, dgsbjtCd, userLat, userLon);
            return HospitalSearchResponse.builder()
                    .hospitals(List.of())
                    .pageNo(1)
                    .numOfRows(0)
                    .totalCount(0)
                    .build();
        }

        // ── dgsbjt 진료과 검증 ────────────────────────────────────────────────
        // 종합병원(clCd=11) / 상급종합(clCd=01): 모든 진료과 보유 가정 → 검증 면제
        // 의원(clCd=31) / 병원(clCd=21): HIRA getDgsbjtInfo 호출(현재 비활성) 또는 이름 기반 fallback으로 검증
        Map<String, HospitalDto> verified = new LinkedHashMap<>();
        List<CompletableFuture<Map.Entry<String, Boolean>>> futures = new ArrayList<>();
        // filtered 삽입 순서를 유지하기 위한 순서 인덱스
        List<String> orderedYkihos = new ArrayList<>(filtered.keySet());

        for (String ykiho : orderedYkihos) {
            HospitalDto dto = filtered.get(ykiho);
            String clCd = dto.getClCd();

            // 종합병원·상급종합 → 검증 면제 즉시 통과
            if ("01".equals(clCd) || "11".equals(clCd)) {
                verified.put(ykiho, dto);
                continue;
            }

            // 의원·병원 → 비동기 검증 (HIRA or 이름 기반 fallback)
            final String finalYkiho = ykiho;
            final HospitalDto finalDto = dto;
            futures.add(CompletableFuture.supplyAsync(() -> {
                Set<String> codes = hiraDgsbjtCacheService.getOrFetch(finalYkiho);
                boolean pass;
                if (codes.isEmpty()) {
                    // fallback: 이름 기반 strict 매칭 (API 미신청 또는 빈 응답 시 동작)
                    pass = DepartmentNameMatcher.matches(finalDto.getHospitalName(), dgsbjtCd);
                    log.debug("dgsbjt 이름 기반 fallback: ykiho={}, yadmNm={}, dgsbjtCd={}, pass={}",
                            finalYkiho, finalDto.getHospitalName(), dgsbjtCd, pass);
                } else {
                    pass = codes.contains(dgsbjtCd);
                    log.debug("dgsbjt 코드 기반 검증: ykiho={}, codes={}, dgsbjtCd={}, pass={}",
                            finalYkiho, codes, dgsbjtCd, pass);
                }
                return Map.entry(finalYkiho, pass);
            }, dgsbjtVerifyExecutor));
        }

        // 비동기 결과 수집 — 삽입 순서 보존을 위해 futures 먼저 전부 resolve 후 orderedYkihos 순서로 채움
        Map<String, Boolean> verifyResults = new HashMap<>();
        for (CompletableFuture<Map.Entry<String, Boolean>> future : futures) {
            try {
                Map.Entry<String, Boolean> result = future.join();
                verifyResults.put(result.getKey(), result.getValue());
            } catch (Exception e) {
                log.warn("dgsbjt 검증 비동기 처리 실패: {}", e.getMessage());
                // 실패 시 해당 병원 제외 (false positive 방지)
            }
        }

        // verified 맵에 통과한 병원만 삽입 (orderedYkihos 순서로 순회 → 삽입 순서 유지)
        for (String ykiho : orderedYkihos) {
            HospitalDto dto = filtered.get(ykiho);
            String clCd = dto.getClCd();
            // 종합·상급종합은 이미 verified에 추가됨 → skip
            if ("01".equals(clCd) || "11".equals(clCd)) continue;
            // 비동기 검증 통과한 병원만 추가
            if (Boolean.TRUE.equals(verifyResults.get(ykiho))) {
                verified.put(ykiho, dto);
            }
        }

        log.debug("dgsbjt 검증 완료: filtered={}, verified={}", filtered.size(), verified.size());

        // verified가 비어있어도 빈 응답 반환 (진료과 불일치로 전부 제외된 케이스)
        if (verified.isEmpty()) {
            log.warn("dgsbjt 검증 후 병원 없음 (dgsbjtCd={}, lat={}, lon={})",
                    dgsbjtCd, userLat, userLon);
            return HospitalSearchResponse.builder()
                    .hospitals(List.of())
                    .pageNo(1)
                    .numOfRows(0)
                    .totalCount(0)
                    .build();
        }
        // ── dgsbjt 검증 완료 ──────────────────────────────────────────────────

        // 단일 IN 쿼리로 인증 병원 ykiho Set 확보 (N+1 방지)
        Set<String> certifiedYkihos = foreignCertifiedHospitalRepository
                .findAllByYkihoIn(verified.keySet())
                .stream()
                .map(ForeignCertifiedHospital::getYkiho)
                .collect(Collectors.toSet());

        List<HospitalDto> hospitals = verified.values().stream()
                .map(dto -> HospitalDto.builder()
                        .ykiho(dto.getYkiho())
                        .hospitalName(dto.getHospitalName())
                        .address(dto.getAddress())
                        .telephone(dto.getTelephone())
                        .longitude(dto.getLongitude())
                        .latitude(dto.getLatitude())
                        .distance(dto.getDistance())
                        .clCd(dto.getClCd())
                        .hospitalType(dto.getHospitalType())
                        .sidoCd(dto.getSidoCd())
                        .sidoCdNm(dto.getSidoCdNm())
                        .dgsbjtCd(dto.getDgsbjtCd())
                        .foreignCertified(certifiedYkihos.contains(dto.getYkiho()))
                        .build())
                .collect(Collectors.toList());

        return HospitalSearchResponse.builder()
                .hospitals(hospitals)
                .pageNo(1)
                .numOfRows(hospitals.size())
                .totalCount(hospitals.size())
                .build();
    }

    // ── 초기 메시지 ──────────────────────────────────────────────────

    private List<GptChatRequest.Message> buildInitialMessages(
            HospitalAssistantRequest req, UserProfileDto profile) {

        String patientContext = profile.isGuest()
                ? "Patient context: Guest user | Risk level: %d/5 (no profile — ICD-10 only cost estimation)".formatted(req.getRiskLevel())
                : "Patient context: Age: %d | Gender: %s | Insured: %s | Risk level: %d/5".formatted(
                        profile.getAge(), profile.getGender(),
                        profile.isInsured() ? "yes" : "no", req.getRiskLevel());

        String systemPrompt = """
                You are a medical AI agent for a hospital-finding application in Korea.
                You MUST call BOTH tools before finishing:
                  1. search_hospitals — analyze symptoms and choose the best HIRA department code
                  2. extract_icd10_code — extract the ICD-10 code for cost estimation

                %s

                Available HIRA department codes:
                  00:일반의, 01:내과, 02:신경과, 03:정신건강의학과, 04:피부과,
                  05:외과, 06:흉부외과, 07:정형외과, 08:신경외과, 09:산부인과,
                  10:소아청소년과, 11:안과, 12:이비인후과, 13:비뇨의학과,
                  18:재활의학과, 20:가정의학과, 21:응급의학과, 24:치과
                """.formatted(patientContext);

        List<GptChatRequest.Message> messages = new ArrayList<>();
        messages.add(new GptChatRequest.Message("system", systemPrompt));
        messages.add(new GptChatRequest.Message("user", req.getSymptom()));
        return messages;
    }

    // ── Tool 정의 ─────────────────────────────────────────────────────

    private List<GptTool> buildTools() {
        GptTool searchHospitals = new GptTool(
                GptTool.Function.builder()
                        .name(TOOL_SEARCH_HOSPITALS)
                        .description("증상을 분석하여 적합한 HIRA 진료과목 코드를 결정합니다. 병원 종별·반경은 서버에서 위험도 기반으로 자동 처리됩니다.")
                        .parameters(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "dgsbjtCd", Map.of(
                                                "type", "string",
                                                "description", "HIRA 진료과목 코드 (예: '01'=내과, '12'=이비인후과)"
                                        ),
                                        "departmentName", Map.of(
                                                "type", "string",
                                                "description", "진료과 이름 (한국어)"
                                        )
                                ),
                                "required", List.of("dgsbjtCd", "departmentName")
                        ))
                        .build()
        );

        GptTool extractIcd10 = new GptTool(
                GptTool.Function.builder()
                        .name(TOOL_EXTRACT_ICD10)
                        .description("증상을 분석하여 ICD-10 코드를 추출합니다. 의료비 예측에 사용됩니다.")
                        .parameters(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "icd10Code", Map.of(
                                                "type", "string",
                                                "description", "ICD-10 코드 (예: 'J06.9', 'M54.5')"
                                        )
                                ),
                                "required", List.of("icd10Code")
                        ))
                        .build()
        );

        return List.of(searchHospitals, extractIcd10);
    }

    // ── HIRA 검색 요청 생성 ───────────────────────────────────────────

    /**
     * 2단계 검색용 HIRA 요청 객체 생성.
     * xPos/yPos/radius는 세팅하지 않으므로 buildUri()에서 자동 제외됨.
     * sidoCd가 null이면 필드도 null로 유지 → buildUri()에서 파라미터 제외(전국 fallback).
     */
    private HospitalSearchRequest buildSearchRequest(
            String dgsbjtCd, String clCd, String sidoCd, int pageNo, int numOfRows) {

        HospitalSearchRequest r = new HospitalSearchRequest();
        r.setDgsbjtCd(dgsbjtCd);
        r.setClCd(clCd);
        r.setSidoCd(sidoCd);
        r.setPageNo(pageNo);
        r.setNumOfRows(numOfRows);
        return r;
    }
}
