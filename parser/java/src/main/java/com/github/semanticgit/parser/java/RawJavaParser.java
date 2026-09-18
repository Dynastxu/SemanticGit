package com.github.semanticgit.parser.java;

import com.github.semanticgit.common.config.ConfigItem;
import com.github.semanticgit.common.entity.EntityLanguage;
import com.github.semanticgit.parser.java.api.EntityChange;
import com.github.semanticgit.parser.java.api.ParsingResult;
import com.github.semanticgit.parser.java.api.RawAbstractParser;
import com.github.semanticgit.parser.java.api.SourceCode;

import java.util.List;
import java.util.Map;

/**
 * TODO: 实现该类
 * @apiNote TODO: 临时 raw 解析器，最终需替换为 {@link JavaParser}
 */
public class RawJavaParser extends RawAbstractParser {
    public RawJavaParser() {
        super();
    }

    public RawJavaParser(Map<String, ConfigItem<?>> configMap) {
        super(configMap);
    }

    @Override
    public ParsingResult parseEntities(SourceCode sourceCode) {
        return null;
    }

    @Override
    public EntityLanguage getSupportedLanguage() {
        return null;
    }

    @Override
    public List<EntityChange> parseChangeNatureFlags(SourceCode sourceCodeBefore, SourceCode sourceCodeAfter) {
        return List.of();
    }

    @Override
    public int parseEntityChangeNatureFlag(String sourceCodeBefore, String sourceCodeAfter) {
        return 0;
    }

    @Override
    public void registerConfigs(Map<String, ConfigItem<?>> configMap) {

    }
}
