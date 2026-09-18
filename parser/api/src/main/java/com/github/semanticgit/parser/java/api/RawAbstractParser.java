package com.github.semanticgit.parser.java.api;

import com.github.semanticgit.common.config.ConfigItem;

import java.util.Map;

/**
 * @apiNote TODO: 临时 raw 解析器，最终需替换为 {@link AbstractParser}。子类必须拥有无参构造函数。
 */
public abstract class RawAbstractParser implements RawLanguageParser {
    private final Map<String, ConfigItem<?>> configMap;

    /**
     * 无参构造函数，用于注册解析器。
     */
    public RawAbstractParser() {
        this(null);
    }

    public RawAbstractParser(Map<String, ConfigItem<?>> configMap) {
        this.configMap = configMap;
    }

    protected ConfigItem<?> getConfig(String name) {
        return configMap.get(name);
    }

    protected long getTimeoutMs() {
        return (long) getConfig(RawLanguageParser.CONFIG_KEY_TIMEOUT).getValue();
    }

    protected long getMaxParseSizeBytes() {
        return (long) getConfig(RawLanguageParser.CONFIG_KEY_MAX_PARSE_SIZE).getValue();
    }

    protected long getMaxRegexSizeBytes() {
        return (long) getConfig(RawLanguageParser.CONFIG_KEY_MAX_REGEX_SIZE).getValue();
    }
}
