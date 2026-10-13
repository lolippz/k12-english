package com.k12english.repo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.k12english.domain.CardRecord;

/** 记忆卡片存取。 */
@Repository
public class CardRepository {

    private static final String COLS = "w, due, stability, difficulty, elapsed_days, scheduled_days, "
            + "learning_steps, reps, lapses, state, last_review, seen, wrong, first_seen";

    private static final RowMapper<CardRecord> MAPPER = (rs, i) -> new CardRecord(
            rs.getString("w"),
            rs.getLong("due"),
            rs.getDouble("stability"),
            rs.getDouble("difficulty"),
            rs.getInt("elapsed_days"),
            rs.getInt("scheduled_days"),
            rs.getInt("learning_steps"),
            rs.getInt("reps"),
            rs.getInt("lapses"),
            rs.getInt("state"),
            (Long) rs.getObject("last_review"),
            rs.getInt("seen"),
            rs.getInt("wrong"),
            rs.getLong("first_seen"));

    private final JdbcTemplate jdbc;

    public CardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CardRecord> findAll() {
        return jdbc.query("SELECT " + COLS + " FROM cards", MAPPER);
    }

    /**
     * 全部卡片，键为小写单词。
     *
     * <p>前端查卡片时用的是 {@code w.toLowerCase()}（见 App.tsx），这里保持一致，
     * 否则 "China" 这类大写开头的词会查不到。
     */
    public Map<String, CardRecord> findAllByLowerWord() {
        Map<String, CardRecord> out = new LinkedHashMap<>();
        for (CardRecord c : findAll()) {
            out.put(c.w().toLowerCase(), c);
        }
        return out;
    }

    public Optional<CardRecord> find(String w) {
        List<CardRecord> rows = jdbc.query(
                "SELECT " + COLS + " FROM cards WHERE w = ?", MAPPER, w);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void save(CardRecord c) {
        jdbc.update("""
                INSERT INTO cards (w, due, stability, difficulty, elapsed_days, scheduled_days,
                                   learning_steps, reps, lapses, state, last_review, seen, wrong, first_seen)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(w) DO UPDATE SET
                    due = excluded.due,
                    stability = excluded.stability,
                    difficulty = excluded.difficulty,
                    elapsed_days = excluded.elapsed_days,
                    scheduled_days = excluded.scheduled_days,
                    learning_steps = excluded.learning_steps,
                    reps = excluded.reps,
                    lapses = excluded.lapses,
                    state = excluded.state,
                    last_review = excluded.last_review,
                    seen = excluded.seen,
                    wrong = excluded.wrong,
                    first_seen = excluded.first_seen
                """,
                c.w(), c.due(), c.stability(), c.difficulty(), c.elapsedDays(), c.scheduledDays(),
                c.learningSteps(), c.reps(), c.lapses(), c.state(), c.lastReview(), c.seen(),
                c.wrong(), c.firstSeen());
    }

    public void saveAll(List<CardRecord> cards) {
        for (CardRecord c : cards) {
            save(c);
        }
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM cards", Long.class);
        return n == null ? 0 : n;
    }

    public void clear() {
        jdbc.update("DELETE FROM cards");
    }
}
