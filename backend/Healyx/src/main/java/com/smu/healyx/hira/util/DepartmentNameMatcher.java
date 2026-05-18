package com.smu.healyx.hira.util;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 병원명(yadmNm) 기반 진료과 매칭 유틸.
 *
 * <p>HIRA {@code getDgsbjtInfo} API가 빈 결과를 반환할 때(미신청 또는 장애)
 * {@code HiraDgsbjtCacheService}가 이 유틸을 fallback으로 사용합니다.
 *
 * <h3>매칭 전략</h3>
 * <ul>
 *   <li>false positive 회피 우선: 안과 검색 시 "곽내과의원", "JS메디칼내과외과정형외과의원" 등은 제외.</li>
 *   <li>false negative 허용: 진료과 미표기("○○○의원")는 제외 허용.</li>
 *   <li>각 dgsbjtCd별로 포함 키워드(includeAny)와 제외 키워드(excludeIfContains)를 정의.<br>
 *       포함 키워드 중 하나가 yadmNm에 있고, 제외 키워드가 없을 때만 통과.</li>
 *   <li>외과(05) 예시: "외과" 포함 AND "신경외과"·"정형외과"·"흉부외과" 미포함.</li>
 * </ul>
 */
public class DepartmentNameMatcher {

    private DepartmentNameMatcher() {
        // 유틸 클래스 — 인스턴스화 방지
    }

    /**
     * dgsbjtCd → 매칭 규칙 매핑.
     * 키: HIRA 진료과목코드 (문자열)
     * 값: {@link MatchRule} (포함 키워드 목록 + 제외 키워드 목록)
     */
    private static final Map<String, MatchRule> RULES = Map.ofEntries(
            // 01 내과 — "내과"를 포함하되 "정신건강의학과" 등은 별도 코드이므로 문제 없음
            Map.entry("01", new MatchRule(
                    List.of("내과"),
                    List.of()
            )),
            // 02 신경과 — "신경과" 포함 AND "신경외과" 미포함
            Map.entry("02", new MatchRule(
                    List.of("신경과"),
                    List.of("신경외과")
            )),
            // 03 정신건강의학과
            Map.entry("03", new MatchRule(
                    List.of("정신건강의학과", "정신과"),
                    List.of()
            )),
            // 04 피부과
            Map.entry("04", new MatchRule(
                    List.of("피부과"),
                    List.of()
            )),
            // 05 외과 — "외과" 포함 AND "신경외과"·"정형외과"·"흉부외과"·"이비인후" 미포함
            Map.entry("05", new MatchRule(
                    List.of("외과"),
                    List.of("신경외과", "정형외과", "흉부외과", "이비인후")
            )),
            // 06 흉부외과
            Map.entry("06", new MatchRule(
                    List.of("흉부외과"),
                    List.of()
            )),
            // 07 정형외과
            Map.entry("07", new MatchRule(
                    List.of("정형외과"),
                    List.of()
            )),
            // 08 신경외과
            Map.entry("08", new MatchRule(
                    List.of("신경외과"),
                    List.of()
            )),
            // 09 산부인과
            Map.entry("09", new MatchRule(
                    List.of("산부인과"),
                    List.of()
            )),
            // 10 소아청소년과
            Map.entry("10", new MatchRule(
                    List.of("소아청소년과", "소아과"),
                    List.of()
            )),
            // 11 안과
            Map.entry("11", new MatchRule(
                    List.of("안과"),
                    List.of()
            )),
            // 12 이비인후과
            Map.entry("12", new MatchRule(
                    List.of("이비인후과"),
                    List.of()
            )),
            // 13 비뇨의학과
            Map.entry("13", new MatchRule(
                    List.of("비뇨의학과", "비뇨기과"),
                    List.of()
            )),
            // 18 재활의학과
            Map.entry("18", new MatchRule(
                    List.of("재활의학과", "재활"),
                    List.of()
            )),
            // 20 가정의학과
            Map.entry("20", new MatchRule(
                    List.of("가정의학과"),
                    List.of()
            )),
            // 21 응급의학과
            Map.entry("21", new MatchRule(
                    List.of("응급"),
                    List.of()
            )),
            // 24 치과
            Map.entry("24", new MatchRule(
                    List.of("치과"),
                    List.of()
            ))
    );

    /**
     * 병원명이 지정 진료과목코드에 해당하는지 이름 기반으로 판단합니다.
     *
     * <p>포함 키워드 중 하나 이상이 {@code yadmNm}에 존재하고,
     * 제외 키워드가 하나도 존재하지 않을 때 {@code true}를 반환합니다.
     *
     * @param yadmNm    병원명 (HIRA 응답의 yadmNm)
     * @param dgsbjtCd  HIRA 진료과목코드
     * @return 해당 병원이 해당 진료과를 보유할 것으로 판단되면 {@code true}
     */
    public static boolean matches(String yadmNm, String dgsbjtCd) {
        if (yadmNm == null || yadmNm.isBlank() || dgsbjtCd == null) return false;

        MatchRule rule = RULES.get(dgsbjtCd);
        if (rule == null) {
            // 매핑 없는 코드 → false negative 허용
            return false;
        }

        // 제외 키워드 중 하나라도 포함되면 즉시 false (false positive 방지)
        for (String exclude : rule.excludeIfContains()) {
            if (yadmNm.contains(exclude)) return false;
        }

        // 포함 키워드 중 하나라도 포함되면 true
        for (String include : rule.includeAny()) {
            if (yadmNm.contains(include)) return true;
        }

        return false;
    }

    /**
     * 진료과 매칭 규칙.
     *
     * @param includeAny          이 중 하나 이상이 병원명에 있어야 함
     * @param excludeIfContains   이 중 하나라도 병원명에 있으면 제외
     */
    private record MatchRule(List<String> includeAny, List<String> excludeIfContains) {}
}
