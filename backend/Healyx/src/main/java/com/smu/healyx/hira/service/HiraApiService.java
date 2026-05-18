package com.smu.healyx.hira.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smu.healyx.common.exception.ExternalApiException;
import com.smu.healyx.hira.dto.HiraDgsbjtItem;
import com.smu.healyx.hira.dto.HiraItem;
import com.smu.healyx.hira.dto.HospitalDto;
import com.smu.healyx.hira.dto.HospitalSearchRequest;
import com.smu.healyx.hira.dto.HospitalSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class HiraApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${api.hira.url}")
    private String hiraApiUrl;

    @Value("${api.hira.key}")
    private String hiraApiKey;

    private static final String ENDPOINT = "/getHospBasisList";

    /**
     * HIRA 진료과목 정보 API endpoint.
     *
     * <p>HIRA 진료과목 정보 API 미신청 상태. 공공데이터포털 API 신청 완료 후 아래
     * {@link #fetchDgsbjtCodes(String)} 본문을 활성화하세요.
     */
    private static final String DGSBJT_ENDPOINT = "/getDgsbjtInfo2.7";

    /**
     * HIRA Open API를 호출하여 병원 목록을 검색합니다.
     * 일 10,000건 트래픽 제한이 있으므로 불필요한 중복 호출을 방지해야 합니다.
     */
    public HospitalSearchResponse searchHospitals(HospitalSearchRequest request) {
        URI uri = buildUri(request);
        log.debug("HIRA API 호출: {}", uri.toString().replaceAll("serviceKey=[^&]+", "serviceKey=***"));

        String rawResponse;
        try {
            rawResponse = restTemplate.getForObject(uri, String.class);  // URI 객체 전달
        } catch (Exception e) {
            log.error("HIRA API 호출 실패: {}", e.getMessage());
            throw new ExternalApiException("HIRA_API_ERROR", "병원 정보 서비스에 일시적으로 접근할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }

        log.debug("HIRA API 원시 응답: {}", rawResponse);
        return parseResponse(rawResponse, request.getPageNo(), request.getNumOfRows());
    }

    /**
     * ykiho에 해당하는 병원의 진료과목 코드 집합 조회.
     *
     * <p><b>현재 상태: HIRA 진료과목 정보 API 미신청 상태. 신청 완료 후 본문 활성화 예정.</b>
     * 항상 {@code Optional.empty()}를 반환하므로 호출 측에서 fallback 처리해야 합니다.
     *
     * <p>활성화 방법: 이 메서드 본문의 {@code return Optional.empty();} 라인을 제거하고
     * 아래 주석 처리된 실제 호출 로직을 활성화하세요.
     *
     * @param ykiho 암호화된 요양기호 (병원 식별 키)
     * @return 진료과목 코드 Set. API 미신청 또는 호출 실패 시 {@code Optional.empty()}.
     */
    public Optional<Set<String>> fetchDgsbjtCodes(String ykiho) {
        // HIRA 진료과목 정보 API 미신청 상태. 신청 완료 후 이 라인을 제거하고 아래 로직을 활성화하세요.
        return Optional.empty();

        /*
         * ── API 신청 완료 후 활성화할 코드 ──────────────────────────────────────
         * String uriStr = hiraApiUrl + DGSBJT_ENDPOINT
         *         + "?serviceKey=" + hiraApiKey
         *         + "&_type=json"
         *         + "&ykiho=" + ykiho
         *         + "&pageNo=1&numOfRows=100";
         * URI uri = URI.create(uriStr);
         * log.debug("HIRA getDgsbjtInfo 호출: ykiho={}", ykiho);
         *
         * try {
         *     String raw = restTemplate.getForObject(uri, String.class);
         *     JsonNode root = objectMapper.readTree(raw);
         *     String resultCode = root.path("response").path("header").path("resultCode").asText();
         *     if (!"00".equals(resultCode)) {
         *         log.warn("HIRA getDgsbjtInfo 오류: ykiho={}, resultCode={}", ykiho, resultCode);
         *         return Optional.empty();
         *     }
         *
         *     JsonNode itemNode = root.path("response").path("body").path("items").path("item");
         *     Set<String> codes = new HashSet<>();
         *     if (itemNode.isArray()) {
         *         for (JsonNode node : itemNode) {
         *             HiraDgsbjtItem item = objectMapper.treeToValue(node, HiraDgsbjtItem.class);
         *             if (StringUtils.hasText(item.getDgsbjtCd())) codes.add(item.getDgsbjtCd());
         *         }
         *     } else if (itemNode.isObject()) {
         *         HiraDgsbjtItem item = objectMapper.treeToValue(itemNode, HiraDgsbjtItem.class);
         *         if (StringUtils.hasText(item.getDgsbjtCd())) codes.add(item.getDgsbjtCd());
         *     }
         *     return codes.isEmpty() ? Optional.empty() : Optional.of(codes);
         *
         * } catch (Exception e) {
         *     log.warn("HIRA getDgsbjtInfo 호출 실패: ykiho={}, error={}", ykiho, e.getMessage());
         *     return Optional.empty();
         * }
         * ─────────────────────────────────────────────────────────────────────
         */
    }

    private URI buildUri(HospitalSearchRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(hiraApiUrl).append(ENDPOINT);
        sb.append("?serviceKey=").append(hiraApiKey);
        sb.append("&pageNo=").append(request.getPageNo());
        sb.append("&numOfRows=").append(request.getNumOfRows());
        sb.append("&_type=json");

        if (StringUtils.hasText(request.getDgsbjtCd())) {
            sb.append("&dgsbjtCd=").append(request.getDgsbjtCd());
        }
        if (StringUtils.hasText(request.getClCd())) {
            sb.append("&clCd=").append(request.getClCd());
        }
        // 병원명 검색 — 한글 입력 가능하므로 URL 인코딩 필수
        if (StringUtils.hasText(request.getYadmNm())) {
            sb.append("&yadmNm=")
              .append(URLEncoder.encode(request.getYadmNm(), StandardCharsets.UTF_8));
        }
        if (request.getXPos() != 0.0) {
            sb.append("&xPos=").append(request.getXPos());
        }
        if (request.getYPos() != 0.0) {
            sb.append("&yPos=").append(request.getYPos());
        }
        if (request.getRadius() > 0) {
            sb.append("&radius=").append(request.getRadius());
        }
        if (StringUtils.hasText(request.getSidoCd())) {
            sb.append("&sidoCd=").append(request.getSidoCd());
        }

        return URI.create(sb.toString());
    }

    private HospitalSearchResponse parseResponse(String rawResponse, int pageNo, int numOfRows) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode header = root.path("response").path("header");

            String resultCode = header.path("resultCode").asText();
            if (!"00".equals(resultCode)) {
                log.error("HIRA API 오류 응답: resultCode={}, resultMsg={}",
                        resultCode, header.path("resultMsg").asText());
                throw new ExternalApiException("HIRA_API_ERROR",
                        "[HIRA resultCode=" + resultCode + "] " + header.path("resultMsg").asText());
            }

            JsonNode body = root.path("response").path("body");
            int totalCount = body.path("totalCount").asInt(0);

            List<HospitalDto> hospitals = new ArrayList<>();
            JsonNode itemNode = body.path("items").path("item");

            if (itemNode.isArray()) {
                for (JsonNode node : itemNode) {
                    HiraItem item = objectMapper.treeToValue(node, HiraItem.class);
                    hospitals.add(toHospitalDto(item));
                }
            } else if (itemNode.isObject()) {
                // 결과가 1건일 때 HIRA API는 배열 대신 단일 객체를 반환
                HiraItem item = objectMapper.treeToValue(itemNode, HiraItem.class);
                hospitals.add(toHospitalDto(item));
            }
            // itemNode가 없거나 빈 문자열이면 빈 리스트 반환

            return HospitalSearchResponse.builder()
                    .hospitals(hospitals)
                    .pageNo(pageNo)
                    .numOfRows(numOfRows)
                    .totalCount(totalCount)
                    .build();

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("HIRA API 응답 파싱 실패", e);
            throw new ExternalApiException("HIRA_PARSE_ERROR", "병원 정보 서비스에 일시적으로 접근할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private HospitalDto toHospitalDto(HiraItem item) {
        return HospitalDto.builder()
                .ykiho(item.getYkiho())
                .hospitalName(item.getYadmNm())
                .address(item.getAddr())
                .telephone(item.getTelno())
                .longitude(item.getLongitude())
                .latitude(item.getLatitude())
                .distance((int) Math.round(item.getDistance()))
                .clCd(item.getClCd())
                .hospitalType(item.getClCdNm())
                .sidoCd(item.getSidoCd())
                .sidoCdNm(item.getSidoCdNm())
                .dgsbjtCd(item.getDgsbjtCd())
                .foreignCertified(false) // DB 연동 완료 후 foreign_certified_hospital 테이블에서 조회
                .build();
    }
}