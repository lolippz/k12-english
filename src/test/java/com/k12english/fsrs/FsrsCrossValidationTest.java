package com.k12english.fsrs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FSRS 交叉验证测试：用 ts-fsrs（Node.js）生成的黄金向量逐值比对 Java 移植。
 *
 * <p>测试数据由 {@code frontend/fsrs-golden.mjs} 产出，覆盖：
 * <ul>
 *   <li>新卡预览（fuzz 开/关、long-term）</li>
 *   <li>连续复习链（Good / 混合）</li>
 *   <li>复习卡预览（含 fuzz）</li>
 *   <li>retrievability</li>
 * </ul>
 */
class FsrsCrossValidationTest {

    private static JsonNode golden;
    private static long baseTime;

    @BeforeAll
    static void loadGolden() throws Exception {
        try (InputStream in = FsrsCrossValidationTest.class.getResourceAsStream("/fsrs-golden.json")) {
            golden = new ObjectMapper().readTree(in);
        }
        baseTime = golden.get("baseTime").asLong();
    }

    @Test
    void allScenarios() {
        for (JsonNode scenario : golden.get("scenarios")) {
            String name = scenario.get("name").asText();
            switch (scenario.get("type").asText()) {
                case "preview" -> runPreview(name, scenario);
                case "sequence" -> runSequence(name, scenario);
                case "retrievability" -> runRetrievability(name, scenario);
                default -> fail("Unknown scenario type: " + scenario.get("type"));
            }
        }
    }

    // ------------------------------------------------------------------

    private void runPreview(String name, JsonNode s) {
        FsrsParameters params = readConfig(s.get("config"));
        FsrsEngine engine = new FsrsEngine(params);
        FsrsCard initial = readCard(s.get("initial"));
        long at = s.get("at").asLong();
        Map<Integer, FsrsReview> actual = engine.repeat(initial, date(at));

        JsonNode expected = s.get("results");
        for (int grade = 1; grade <= 4; grade++) {
            JsonNode exp = expected.get(String.valueOf(grade));
            assertNotNull(exp, name + " missing grade " + grade);
            assertReview(name + "[grade=" + grade + "]", exp, actual.get(grade));
        }
    }

    private void runSequence(String name, JsonNode s) {
        FsrsParameters params = readConfig(s.get("config"));
        FsrsEngine engine = new FsrsEngine(params);
        FsrsCard card = FsrsEngine.emptyCard(date(0));

        int stepIndex = 0;
        for (JsonNode step : s.get("records")) {
            String label = name + "[step=" + stepIndex + "]";
            long at = step.get("at").asLong();
            int grade = step.get("grade").asInt();
            FsrsReview actual = engine.next(card, date(at), grade);
            assertReview(label, step, actual);
            card = actual.card();
            stepIndex++;
        }
    }

    private void runRetrievability(String name, JsonNode s) {
        FsrsParameters params = readConfig(s.get("config"));
        FsrsEngine engine = new FsrsEngine(params);
        FsrsCard card = readCard(s.get("initial"));

        int i = 0;
        for (JsonNode check : s.get("results")) {
            String label = name + "[i=" + i + "]";
            long at = check.get("at").asLong();
            double expected = check.get("value").asDouble();
            double actual = engine.retrievability(card, date(at));
            assertEquals(expected, actual, 1e-9, label);
            i++;
        }
    }

    // ------------------------------------------------------------------

    private static FsrsParameters readConfig(JsonNode cfg) {
        FsrsParameters.Builder b = FsrsParameters.builder();
        if (cfg.has("request_retention")) {
            b.requestRetention(cfg.get("request_retention").asDouble());
        }
        if (cfg.has("enable_fuzz")) {
            b.enableFuzz(cfg.get("enable_fuzz").asBoolean());
        }
        if (cfg.has("enable_short_term")) {
            b.enableShortTerm(cfg.get("enable_short_term").asBoolean());
        }
        return b.build();
    }

    private static FsrsCard readCard(JsonNode n) {
        FsrsCard.Builder b = FsrsCard.builder()
            .due(new Date(n.get("due").asLong()))
            .stability(n.get("stability").asDouble())
            .difficulty(n.get("difficulty").asDouble())
            .elapsedDays(n.get("elapsed_days").asInt())
            .scheduledDays(n.get("scheduled_days").asInt())
            .learningSteps(n.get("learning_steps").asInt())
            .reps(n.get("reps").asInt())
            .lapses(n.get("lapses").asInt())
            .state(n.get("state").asInt());
        JsonNode lr = n.get("last_review");
        if (lr != null && !lr.isNull()) {
            b.lastReview(new Date(lr.asLong()));
        }
        return b.build();
    }

    private static void assertReview(String label, JsonNode expected, FsrsReview actual) {
        assertCard(label + ".card", expected.get("card"), actual.card());
        assertLog(label + ".log", expected.get("log"), actual.log());
    }

    private static void assertCard(String label, JsonNode expected, FsrsCard actual) {
        assertEquals(expected.get("due").asLong(), actual.due().getTime(), label + ".due");
        assertEquals(expected.get("stability").asDouble(), actual.stability(), 1e-9, label + ".stability");
        assertEquals(expected.get("difficulty").asDouble(), actual.difficulty(), 1e-9, label + ".difficulty");
        assertEquals(expected.get("elapsed_days").asInt(), actual.elapsedDays(), label + ".elapsedDays");
        assertEquals(expected.get("scheduled_days").asInt(), actual.scheduledDays(), label + ".scheduledDays");
        assertEquals(expected.get("learning_steps").asInt(), actual.learningSteps(), label + ".learningSteps");
        assertEquals(expected.get("reps").asInt(), actual.reps(), label + ".reps");
        assertEquals(expected.get("lapses").asInt(), actual.lapses(), label + ".lapses");
        assertEquals(expected.get("state").asInt(), actual.state(), label + ".state");
        JsonNode lr = expected.get("last_review");
        if (lr == null || lr.isNull()) {
            assertNull(actual.lastReview(), label + ".lastReview");
        } else {
            assertNotNull(actual.lastReview(), label + ".lastReview");
            assertEquals(lr.asLong(), actual.lastReview().getTime(), label + ".lastReview");
        }
    }

    private static void assertLog(String label, JsonNode expected, FsrsReviewLog actual) {
        assertEquals(expected.get("rating").asInt(), actual.rating(), label + ".rating");
        assertEquals(expected.get("state").asInt(), actual.state(), label + ".state");
        assertEquals(expected.get("due").asLong(), actual.due().getTime(), label + ".due");
        assertEquals(expected.get("stability").asDouble(), actual.stability(), 1e-9, label + ".stability");
        assertEquals(expected.get("difficulty").asDouble(), actual.difficulty(), 1e-9, label + ".difficulty");
        assertEquals(expected.get("elapsed_days").asInt(), actual.elapsedDays(), label + ".elapsedDays");
        assertEquals(expected.get("last_elapsed_days").asInt(), actual.lastElapsedDays(), label + ".lastElapsedDays");
        assertEquals(expected.get("scheduled_days").asInt(), actual.scheduledDays(), label + ".scheduledDays");
        assertEquals(expected.get("learning_steps").asInt(), actual.learningSteps(), label + ".learningSteps");
        assertEquals(expected.get("review").asLong(), actual.review().getTime(), label + ".review");
    }

    private static Date date(long offsetMs) {
        return new Date(baseTime + offsetMs);
    }
}
