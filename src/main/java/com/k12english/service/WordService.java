package com.k12english.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;
import com.k12english.config.AppProperties;
import com.k12english.domain.Settings;
import com.k12english.domain.Word;

/**
 * 词库服务。启动时一次性把 words.json 读进内存，之后所有筛选都在内存里做。
 *
 * <p>6877 个词条约占 8 MB 堆内存，换来的是零查询延迟——单机应用这个交换很划算。
 *
 * <p>数据文件由 Maven 的 {@code with-words} profile 从 {@code tools/out/words.json}
 * 复制到 classpath 的 {@code data/} 下。
 */
@Service
public class WordService {

    private static final Logger log = LoggerFactory.getLogger(WordService.class);

    private final List<Word> words;
    private final Map<String, Word> byLowerWord;
    private final Map<String, Object> meta;

    public WordService(AppProperties props, ObjectMapper json) {
        List<Word> loaded = List.of();
        Map<String, Object> loadedMeta = Map.of();
        String resource = props.getWordsResource();
        ClassPathResource res = new ClassPathResource(resource);
        if (!res.exists()) {
            log.warn("词库数据 {} 不存在。请先跑 tools 下的数据流水线，再执行 mvn package。"
                    + "当前将以空词库启动，接口可用但没有单词。", resource);
        } else {
            try (InputStream in = res.getInputStream()) {
                Envelope env = json.readValue(in, Envelope.class);
                loaded = env.words() == null ? List.of() : List.copyOf(env.words());
                loadedMeta = env.meta() == null ? Map.of() : env.meta();
                log.info("词库载入 {} 个词条", loaded.size());
            } catch (Exception e) {
                log.error("词库数据解析失败，将以空词库启动", e);
            }
        }
        this.words = loaded;
        this.meta = loadedMeta;
        Map<String, Word> idx = new LinkedHashMap<>();
        for (Word w : loaded) {
            idx.putIfAbsent(w.w().toLowerCase(), w);
        }
        this.byLowerWord = Map.copyOf(idx);
    }

    public int size() {
        return words.size();
    }

    public Map<String, Object> meta() {
        return meta;
    }

    /** 全部词条（按词表原始顺序，即教材单元顺序）。 */
    public List<Word> all() {
        return words;
    }

    public Optional<Word> find(String w) {
        return w == null ? Optional.empty() : Optional.ofNullable(byLowerWord.get(w.toLowerCase()));
    }

    /** 按学段筛选。学段为空视为全选。 */
    public List<Word> ofStages(List<String> stages) {
        if (stages == null || stages.isEmpty()) {
            return words;
        }
        Set<String> want = new LinkedHashSet<>(stages);
        List<Word> out = new ArrayList<>();
        for (Word w : words) {
            for (String s : w.stages()) {
                if (want.contains(s)) {
                    out.add(w);
                    break;
                }
            }
        }
        return out;
    }

    /** 某组学段下出现过的全部册次，按中文拼音序。 */
    public List<String> booksOfStages(List<String> stages) {
        Set<String> set = new LinkedHashSet<>();
        for (Word w : ofStages(stages)) {
            set.addAll(w.books());
        }
        List<String> out = new ArrayList<>(set);
        out.sort(String::compareTo);
        return out;
    }

    /** 学段 + 册次双重筛选。册次为空表示该学段全部。 */
    public List<Word> ofBooks(List<String> stages, List<String> books) {
        List<Word> base = ofStages(stages);
        if (books == null || books.isEmpty()) {
            return base;
        }
        Set<String> want = new LinkedHashSet<>(books);
        List<Word> out = new ArrayList<>();
        for (Word w : base) {
            for (String b : w.books()) {
                if (want.contains(b)) {
                    out.add(w);
                    break;
                }
            }
        }
        return out;
    }

    /** 按设置筛选出的学习词池。 */
    public List<Word> poolOf(Settings s) {
        return ofBooks(s.stages(), s.books());
    }

    /** words.json 的外层结构。 */
    private record Envelope(Map<String, Object> meta, List<Word> words) {
    }

    /** 供健康检查用：学段列表。 */
    public List<String> allStages() {
        return Settings.ALL_STAGES;
    }
}
