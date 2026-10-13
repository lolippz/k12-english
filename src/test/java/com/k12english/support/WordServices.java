package com.k12english.support;

import com.k12english.config.AppProperties;
import com.k12english.service.WordService;

import tools.jackson.databind.json.JsonMapper;

/** 测试用词库服务工厂。 */
public final class WordServices {

    private WordServices() {
    }

    /** 6 个词的小词表（src/test/resources/data/tiny-words.json），断言可控。 */
    public static WordService tiny() {
        AppProperties props = new AppProperties();
        props.setWordsResource("data/tiny-words.json");
        return new WordService(props, JsonMapper.builder().build());
    }

    /** 真实词库。需要先跑过 tools 数据流水线；缺失时返回空词库而不是抛异常。 */
    public static WordService real() {
        AppProperties props = new AppProperties();
        props.setWordsResource("data/words.json");
        return new WordService(props, JsonMapper.builder().build());
    }
}
