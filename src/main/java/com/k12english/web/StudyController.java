package com.k12english.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.k12english.domain.Settings;
import com.k12english.repo.SettingsRepository;
import com.k12english.service.StudyService;

/** 今日学习队列。 */
@RestController
@RequestMapping("/api/study")
public class StudyController {

    private final StudyService study;
    private final SettingsRepository settings;

    public StudyController(StudyService study, SettingsRepository settings) {
        this.study = study;
        this.settings = settings;
    }

    /**
     * 生成今日队列。
     *
     * @param now 可选的当前时间戳（epoch ms），便于测试复现；不传则用服务器时间
     */
    @GetMapping("/queue")
    public StudyService.QueueResult queue(@RequestParam(name = "now", required = false) Long now) {
        Settings s = settings.load();
        return study.buildQueue(s, now == null ? System.currentTimeMillis() : now);
    }
}
