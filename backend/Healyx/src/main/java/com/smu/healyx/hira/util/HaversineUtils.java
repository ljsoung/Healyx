package com.smu.healyx.hira.util;

/**
 * 두 GPS 좌표 간 거리(m)를 Haversine 공식으로 계산하는 정적 유틸.
 * 지구 반경 6,371,000m 기준.
 */
public class HaversineUtils {

    private HaversineUtils() {
        // 유틸 클래스 — 인스턴스화 방지
    }

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    /**
     * 두 지점 사이의 거리를 미터 단위로 반환합니다.
     *
     * @param lat1 출발지 위도
     * @param lon1 출발지 경도
     * @param lat2 목적지 위도
     * @param lon2 목적지 경도
     * @return 거리 (m)
     */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
