package com.smu.healyx.community.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smu.healyx.common.exception.AuthException;
import com.smu.healyx.gpt.service.GptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 클린봇(ContentFilterService) 정탐률(Recall)·오탐률(FPR) 측정 통합 테스트.
 * - 실제 OpenAI API 호출 (GPT_API_KEY 환경변수 필요, CI에서는 자동 skip).
 * - 표본: src/test/resources/cleanbot/samples.json (HARMFUL 20 + CLEAN 20).
 * - 결과는 표준 출력 표로 출력. 어설션은 매우 느슨한 sanity bound만 둡니다.
 */
class ContentFilterAccuracyTest {

    private static final String API_KEY = System.getenv("GPT_API_KEY");
    private static final long CALL_INTERVAL_MS = 150L;

    private ContentFilterService contentFilterService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        assumeTrue(API_KEY != null && !API_KEY.isBlank(),
                "GPT_API_KEY 환경변수가 설정되지 않아 테스트를 건너뜁니다.");
        objectMapper = new ObjectMapper();
        GptService gptService = new GptService(new RestTemplate(), objectMapper);
        ReflectionTestUtils.setField(gptService, "gptApiKey", API_KEY);
        contentFilterService = new ContentFilterService(gptService);
    }

    @Test
    @DisplayName("[실제 API] 클린봇 정탐률·오탐률 측정 - 라벨 표본")
    void measureRecallAndFpr_overall() throws Exception {
        List<Sample> samples = loadSamples();
        assertThat(samples).as("표본 데이터셋이 로드되어야 합니다").isNotEmpty();

        int tp = 0, fn = 0, fp = 0, tn = 0;
        Map<String, int[]> harmfulPerCat = new LinkedHashMap<>();
        Map<String, int[]> cleanPerCat = new LinkedHashMap<>();
        List<String> misclassified = new ArrayList<>();

        for (Sample s : samples) {
            boolean blocked = invokeCleanbot(s);
            boolean isHarmful = "HARMFUL".equalsIgnoreCase(s.label);

            if (isHarmful) {
                int[] bucket = harmfulPerCat.computeIfAbsent(s.category, k -> new int[2]);
                bucket[1]++;
                if (blocked) {
                    tp++;
                    bucket[0]++;
                } else {
                    fn++;
                    misclassified.add(String.format("- %s (HARMFUL→PASS): %s", s.id, preview(s)));
                }
            } else {
                int[] bucket = cleanPerCat.computeIfAbsent(s.category, k -> new int[2]);
                bucket[1]++;
                if (blocked) {
                    fp++;
                    bucket[0]++;
                    misclassified.add(String.format("- %s (CLEAN→BLOCK): %s", s.id, preview(s)));
                } else {
                    tn++;
                }
            }
            sleepQuietly(CALL_INTERVAL_MS);
        }

        double recall = safeRatio(tp, tp + fn);
        double fpr = safeRatio(fp, fp + tn);
        double precision = safeRatio(tp, tp + fp);
        double f1 = (precision + recall == 0) ? 0.0 : 2 * precision * recall / (precision + recall);

        printReport(samples.size(), tp, fn, fp, tn, recall, fpr, precision, f1,
                harmfulPerCat, cleanPerCat, misclassified);

        // 매우 느슨한 sanity bound (40건 표본 통계 신뢰성 낮음, 메커니즘 검증용).
        assertThat(recall)
                .as("정탐률(Recall)이 비현실적으로 낮으면 클린봇 또는 표본 검토 필요")
                .isGreaterThanOrEqualTo(0.6);
        assertThat(fpr)
                .as("오탐률(FPR)이 과도하게 높으면 클린봇 또는 표본 검토 필요")
                .isLessThanOrEqualTo(0.3);
    }

    private boolean invokeCleanbot(Sample s) {
        try {
            contentFilterService.filterWithLLM(s.title, s.content);
            return false;
        } catch (AuthException e) {
            return "CLEANBOT_BLOCKED".equals(e.getErrorCode());
        }
    }

    private List<Sample> loadSamples() throws Exception {
        try (InputStream is = new ClassPathResource("cleanbot/samples.json").getInputStream()) {
            SampleDataset dataset = objectMapper.readValue(is, SampleDataset.class);
            return dataset.samples;
        }
    }

    private static double safeRatio(int num, int den) {
        return den == 0 ? 0.0 : ((double) num) / den;
    }

    private static String preview(Sample s) {
        String joined = (s.title == null ? "" : s.title + " | ") + (s.content == null ? "" : s.content);
        return joined.length() > 60 ? joined.substring(0, 60) + "..." : joined;
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static void printReport(int n, int tp, int fn, int fp, int tn,
                                    double recall, double fpr, double precision, double f1,
                                    Map<String, int[]> harmfulPerCat,
                                    Map<String, int[]> cleanPerCat,
                                    List<String> misclassified) {
        System.out.println();
        System.out.printf("=== Cleanbot Accuracy (n=%d) ===%n", n);
        System.out.printf("TP=%d  FN=%d   FP=%d   TN=%d%n", tp, fn, fp, tn);
        System.out.printf("Recall (정탐률) = %.3f%n", recall);
        System.out.printf("FPR    (오탐률) = %.3f%n", fpr);
        System.out.printf("Precision       = %.3f%n", precision);
        System.out.printf("F1              = %.3f%n", f1);

        System.out.println();
        System.out.println("=== Per harmful category Recall ===");
        harmfulPerCat.forEach((cat, arr) ->
                System.out.printf("%-16s %d/%d  %5.1f%%%n", cat, arr[0], arr[1],
                        safeRatio(arr[0], arr[1]) * 100));

        System.out.println();
        System.out.println("=== Per clean category FPR ===");
        cleanPerCat.forEach((cat, arr) ->
                System.out.printf("%-16s %d/%d  %5.1f%%%n", cat, arr[0], arr[1],
                        safeRatio(arr[0], arr[1]) * 100));

        if (!misclassified.isEmpty()) {
            System.out.println();
            System.out.println("=== Misclassified samples ===");
            misclassified.forEach(System.out::println);
        }
        System.out.println();
    }

    public static class SampleDataset {
        public List<Sample> samples;
    }

    public static class Sample {
        public String id;
        public String label;
        public String category;
        public String title;
        public String content;
    }
}
