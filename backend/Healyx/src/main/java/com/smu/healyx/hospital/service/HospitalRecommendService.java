package com.smu.healyx.hospital.service;

import com.smu.healyx.hospital.domain.Hospital;
import com.smu.healyx.hospital.dto.*;
import com.smu.healyx.hospital.repository.HospitalDepartmentRepository;
import com.smu.healyx.hospital.repository.HospitalRepository;
import com.smu.healyx.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

import static com.google.common.graph.ElementOrder.sorted;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HospitalRecommendService {

    private final HospitalRepository           hospitalRepository;
    private final HospitalDepartmentRepository hospitalDepartmentRepository;
    private final ReviewRepository             reviewRepository;
    private final GptSymptomService            gptSymptomService;

    private static final int TOP_N = 5;

    public HospitalRecommendResponse recommend(HospitalRecommendRequest request) {
        // 1. GPT 증상 분석: 프론트에서 riskLevel 넘어오면 GPT 결과 무시하고 그 값 사용
        SymptomAnalysisResult analysis = gptSymptomService.analyzeSymptom(request.getSymptom());
        int riskLevel = (request.getRiskLevel() != null)
                ? request.getRiskLevel()
                : analysis.getRiskLevel();
        String department = analysis.getDepartment();

        // 2. 긴급도별 탐색 반경
        double radiusKm = resolveRadius(riskLevel);

        // 3. 반경 내 해당 진료과 보유 병원 조회
        List<Hospital> candidates = hospitalRepository.findHospitalsWithinRadius(
                request.getLatitude(), request.getLongitude(), radiusKm, department);

        // 4. 스코어링 → 정렬 → Top 5
        Comparator<HospitalSummaryDto> comparator = request.isDistanceSort()
                ? Comparator.comparingDouble(HospitalSummaryDto::getDistanceKm)
                : Comparator.comparingDouble(HospitalSummaryDto::getScore).reversed();

        List<HospitalSummaryDto> result = candidates.stream()
                .map(h -> buildScoredDto(h, request.getLatitude(), request.getLongitude(), radiusKm, riskLevel))
                .sorted(comparator)
                .limit(TOP_N)
                .toList();

        boolean hasResult = !result.isEmpty();
        String emptyReason = hasResult ? null :
                department + " 진료과 병원이 " + radiusKm + "km 이내에 없습니다.";

        return HospitalRecommendResponse.builder()
                .riskLevel(riskLevel)
                .department(department)
                .searchRadiusKm(radiusKm)
                .hospitals(result)
                .hasResult(hasResult)
                .emptyReason(emptyReason)
                .build();
    }

    // ─────────────────────────────────────────────
    // private helpers
    // ─────────────────────────────────────────────

    private double resolveRadius(int riskLevel) {
        if (riskLevel <= 2) return 3.0;
        if (riskLevel <= 4) return 10.0;
        return 15.0;
    }

    private HospitalSummaryDto buildScoredDto(Hospital h, double userLat, double userLng,
                                              double radiusKm, int riskLevel) {
        Long id = h.getHospitalId();

        Double avgRating  = reviewRepository.findAvgRatingByHospitalId(id);
        int    reviewCount = reviewRepository.countByHospitalId(id);

        double distKm = haversineKm(userLat, userLng,
                h.getLatitude().doubleValue(), h.getLongitude().doubleValue());

        List<String> depts = hospitalDepartmentRepository
                .findByHospital_HospitalId(id).stream()
                .map(d -> d.getDepartmentName())
                .toList();

        double score = calculateScore(h, avgRating, reviewCount, distKm, radiusKm, riskLevel);

        return HospitalSummaryDto.builder()
                .hospitalId(id)
                .name(h.getName())
                .type(h.getType())
                .address(h.getAddress())
                .latitude(h.getLatitude().doubleValue())
                .longitude(h.getLongitude().doubleValue())
                .phone(h.getPhone())
                .isForeignCertified(h.isForeignCertified())
                .departments(depts)
                .avgRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : null)
                .reviewCount(reviewCount)
                .distanceKm(Math.round(distKm * 100.0) / 100.0)
                .score(Math.round(score * 1000.0) / 1000.0)
                .build();
    }

    /**
     * 스코어링 공식
     * - 리뷰 10건 이상: 진료과(0.35) + 외국어(0.30) + 평점(0.15) + 거리(0.20)
     * - 리뷰 10건 미만: 병원타입(0.50) + 외국어(0.30) + 거리(0.20)
     */
    private double calculateScore(Hospital h, Double avgRating, int reviewCount,
                                  double distKm, double radiusKm, int riskLevel) {
        double foreignScore  = h.isForeignCertified() ? 1.0 : 0.0;
        double distanceScore = Math.max(0.0, (radiusKm - distKm) / radiusKm);

        if (reviewCount < 10) {
            double typeScore = hospitalTypeScore(h.getType(), riskLevel);
            return 0.50 * typeScore + 0.30 * foreignScore + 0.20 * distanceScore;
        } else {
            double ratingScore = (avgRating != null) ? avgRating / 5.0 : 0.0;
            // 진료과 점수: 쿼리에서 이미 필터링했으므로 항상 1.0
            return 0.35 * 1.0 + 0.30 * foreignScore + 0.15 * ratingScore + 0.20 * distanceScore;
        }
    }

    /**
     * 긴급도별 선호 병원 유형 점수
     * 1~2(의원급) / 3~4(병원급) / 5(상급종합/응급)
     */
    private double hospitalTypeScore(String type, int riskLevel) {
        if (type == null) return 0.5;

        boolean isTertiary      = type.contains("상급");
        boolean isGeneral       = type.contains("종합병원") && !isTertiary;
        boolean isHospital      = type.contains("병원") && !isGeneral && !isTertiary;
        boolean isClinique      = type.contains("의원");

        return switch (riskLevel <= 2 ? "low" : riskLevel <= 4 ? "mid" : "high") {
            case "low"  -> isClinique ? 1.0 : isHospital ? 0.6 : isGeneral ? 0.3 : isTertiary ? 0.1 : 0.5;
            case "mid"  -> isClinique ? 0.3 : isHospital ? 1.0 : isGeneral ? 0.8 : isTertiary ? 0.5 : 0.5;
            case "high" -> isClinique ? 0.1 : isHospital ? 0.5 : isGeneral ? 0.8 : isTertiary ? 1.0 : 0.5;
            default     -> 0.5;
        };
    }

    /** Haversine 공식 — 두 좌표 간 거리(km) */
    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}