package com.k12english;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.k12english.config.AppProperties;

/**
 * K12 英语学习应用（单机版）。
 *
 * <p>启动后监听本机端口，浏览器访问即可使用。学习进度存在用户目录下的 SQLite 文件里，
 * 词库与发音音频随 jar 一起分发，全程离线。
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class K12EnglishApplication {

    public static void main(String[] args) {
        SpringApplication.run(K12EnglishApplication.class, args);
    }
}
