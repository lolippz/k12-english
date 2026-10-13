package com.k12english.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.k12english.domain.Settings;
import com.k12english.repo.SettingsRepository;

/** 学习设置读写。 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsRepository settings;

    public SettingsController(SettingsRepository settings) {
        this.settings = settings;
    }

    @GetMapping
    public Settings get() {
        return settings.load();
    }

    @PutMapping
    public Settings put(@RequestBody Settings next) {
        return settings.save(next);
    }
}
