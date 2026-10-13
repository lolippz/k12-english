package com.k12english.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.k12english.domain.Settings;
import com.k12english.repo.SettingsRepository;
import com.k12english.service.StatsService;

/** 学情聚合。 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService stats;
    private final SettingsRepository settings;

    public StatsController(StatsService stats, SettingsRepository settings) {
        this.stats = stats;
        this.settings = settings;
    }

    @GetMapping
    public StatsService.Stats get(@RequestParam(name = "now", required = false) Long now) {
        Settings s = settings.load();
        return stats.compute(s, now == null ? System.currentTimeMillis() : now);
    }
}
