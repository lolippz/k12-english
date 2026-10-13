package com.k12english.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.k12english.domain.Word;
import com.k12english.repo.CardRepository;
import com.k12english.repo.LogRepository;
import com.k12english.service.WordService;
import com.k12english.web.dto.MetaResponse;
import com.k12english.web.dto.WordListResponse;

/** 词库与元信息。 */
@RestController
@RequestMapping("/api")
public class WordController {

    private final WordService words;
    private final CardRepository cards;
    private final LogRepository logs;

    public WordController(WordService words, CardRepository cards, LogRepository logs) {
        this.words = words;
        this.cards = cards;
        this.logs = logs;
    }

    /** 服务存活与词库规模，前端启动时用它判断后端是否就绪。 */
    @GetMapping("/meta")
    public MetaResponse meta() {
        return new MetaResponse("k12-english", "0.1.0", words.size(), cards.count(), logs.count(),
                words.allStages());
    }

    /** 全量词库。 */
    @GetMapping("/words")
    public WordListResponse all() {
        return new WordListResponse(words.meta(), words.all());
    }

    /** 按学段（可叠加册次）筛选词池。 */
    @GetMapping("/words/pool")
    public List<Word> pool(
            @RequestParam(name = "stages", required = false) List<String> stages,
            @RequestParam(name = "books", required = false) List<String> books) {
        return words.ofBooks(stages, books);
    }

    /** 某组学段下出现过的册次。 */
    @GetMapping("/books")
    public List<String> books(@RequestParam(name = "stages", required = false) List<String> stages) {
        return words.booksOfStages(stages);
    }

    /** 学段清单。 */
    @GetMapping("/stages")
    public List<String> stages() {
        return words.allStages();
    }
}
