package com.github.semanticgit.parser.java.api;

import com.github.semanticgit.common.config.ConfigItem;

import java.util.Map;

public abstract class AbstractParser implements LanguageParser {
    private final Map<String, ConfigItem<?>> configMap;

    /**
     * 无参构造函数，用于注册解析器。
     */
    public AbstractParser() {
        this(null);
    }

    public AbstractParser(Map<String, ConfigItem<?>> configMap) {
        this.configMap = configMap;
    }

    protected ConfigItem<?> getConfig(String name) {
        return configMap.get(name);
    }

    protected long getTimeoutMs() {
        return (long) getConfig(LanguageParser.CONFIG_KEY_TIMEOUT).getValue();
    }

    protected long getMaxParseSizeBytes() {
        return (long) getConfig(LanguageParser.CONFIG_KEY_MAX_PARSE_SIZE).getValue();
    }

    protected long getMaxRegexSizeBytes() {
        return (long) getConfig(LanguageParser.CONFIG_KEY_MAX_REGEX_SIZE).getValue();
    }
}
