package com.github.semanticgit.parser.java.api;

import com.github.semanticgit.common.config.ConfigItem;
import com.github.semanticgit.common.config.ConfigItems;
import com.github.semanticgit.common.entity.ChangeNatureFlag;
import com.github.semanticgit.common.entity.EntityLanguage;
import org.jspecify.annotations.NonNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * @apiNote TODO: 临时 raw 接口，最终需替换为 {@link LanguageParser}
 */
public interface RawLanguageParser {
    /**
     * 解析器超时时间，单位毫秒
     */
    String CONFIG_KEY_TIMEOUT = "timeout_ms";
    /**
     * 最大解析文件大小，单位字节
     */
    String CONFIG_KEY_MAX_PARSE_SIZE = "max_parse_size_bytes";
    /**
     * 最大正则解析大小，单位字节
     */
    String CONFIG_KEY_MAX_REGEX_SIZE = "max_regex_size_bytes";

    static void registerCommonConfigs(final @NonNull Map<String, ConfigItem<?>> configMap) {
        configMap.put(CONFIG_KEY_TIMEOUT, ConfigItems.LONG(10000).build());
        configMap.put(CONFIG_KEY_MAX_PARSE_SIZE, ConfigItems.LONG(1024 * 1024 * 10).build());
        configMap.put(CONFIG_KEY_MAX_REGEX_SIZE, ConfigItems.LONG(1024 * 1024 * 1000).build());
    }

    /**
     * 解析源码文件，提取类、方法等实体信息。
     */
    ParsingResult parseEntities(SourceCode sourceCode);

    /**
     * 声明该解析器支持的语言类型
     */
    EntityLanguage getSupportedLanguage();

    /**
     * 解析所有实体的变更行为标志位掩码。
     *
     * @param sourceCodeBefore 变更前文件
     * @param sourceCodeAfter  变更后文件
     * @return 所有实体的变更行为
     * @see #parseEntityChangeNatureFlag
     */
    List<EntityChange> parseChangeNatureFlags(SourceCode sourceCodeBefore, SourceCode sourceCodeAfter);

    /**
     * 解析指定实体的变更行为标志位掩码。参数应当仅包含该实体的完整原始内容，包括注释。
     *
     * @param sourceCodeBefore 变更前实体代码
     * @param sourceCodeAfter  变更后实体代码
     * @return {@link ChangeNatureFlag#codeOf(EnumSet)} 位掩码
     */
    int parseEntityChangeNatureFlag(String sourceCodeBefore, String sourceCodeAfter);

    /**
     * 注册配置项
     *
     * @param configMap 被注册的配置项映射，键为配置项名称，值为配置项实例
     */
    void registerConfigs(final Map<String, ConfigItem<?>> configMap);
}
