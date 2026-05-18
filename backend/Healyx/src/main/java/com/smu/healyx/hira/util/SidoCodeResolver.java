package com.smu.healyx.hira.util;

/**
 * GPS 좌표(위도·경도)를 HIRA sidoCd(6자리)로 변환하는 정적 유틸.
 * 한국 17개 시도의 bounding box 기반으로 매칭하며,
 * 경계 지역 또는 해상에서는 null을 반환합니다.
 */
public class SidoCodeResolver {

    private SidoCodeResolver() {
        // 유틸 클래스 — 인스턴스화 방지
    }

    private record SidoBbox(String sidoCd, double minLat, double maxLat, double minLon, double maxLon) {}

    private static final SidoBbox[] SIDO_BBOXES = {
        new SidoBbox("110000", 37.413, 37.702, 126.734, 127.269), // 서울특별시
        new SidoBbox("210000", 35.044, 35.396, 128.739, 129.315), // 부산광역시
        new SidoBbox("220000", 35.658, 36.002, 128.375, 128.753), // 대구광역시
        new SidoBbox("230000", 37.153, 37.831, 126.387, 126.784), // 인천광역시
        new SidoBbox("240000", 35.056, 35.269, 126.699, 126.952), // 광주광역시
        new SidoBbox("250000", 36.198, 36.497, 127.253, 127.539), // 대전광역시
        new SidoBbox("260000", 35.425, 35.738, 129.012, 129.385), // 울산광역시
        new SidoBbox("290000", 36.374, 36.713, 127.179, 127.449), // 세종특별자치시
        new SidoBbox("310000", 36.929, 38.312, 126.398, 127.869), // 경기도
        new SidoBbox("320000", 37.007, 38.615, 127.071, 129.376), // 강원도
        new SidoBbox("330000", 36.161, 37.195, 127.393, 128.531), // 충청북도
        new SidoBbox("340000", 35.946, 37.001, 125.978, 127.394), // 충청남도
        new SidoBbox("350000", 35.244, 36.019, 126.315, 127.609), // 전라북도
        new SidoBbox("360000", 34.174, 35.342, 125.971, 127.720), // 전라남도
        new SidoBbox("370000", 35.562, 37.105, 127.965, 129.586), // 경상북도
        new SidoBbox("380000", 34.615, 35.672, 127.531, 129.217), // 경상남도
        new SidoBbox("390000", 33.107, 33.959, 126.148, 126.978), // 제주특별자치도
    };

    /**
     * GPS 좌표를 HIRA sidoCd로 변환합니다.
     *
     * @param lat 위도
     * @param lon 경도
     * @return HIRA 6자리 sidoCd (예: "110000"), 매칭 실패 시 null
     */
    public static String resolve(double lat, double lon) {
        for (SidoBbox bbox : SIDO_BBOXES) {
            if (lat >= bbox.minLat() && lat <= bbox.maxLat()
                    && lon >= bbox.minLon() && lon <= bbox.maxLon()) {
                return bbox.sidoCd();
            }
        }
        return null;
    }
}
