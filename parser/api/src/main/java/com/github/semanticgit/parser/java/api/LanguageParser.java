package com.github.semanticgit.parser.java.api;

import com.github.semanticgit.common.entity.ChangeNatureFlag;
import com.github.semanticgit.common.entity.EntityLanguage;

import java.util.EnumSet;
import java.util.List;

public interface LanguageParser<C extends ParserConfig> {
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
     * @param sourceCodeBefore 变更前文件
     * @param sourceCodeAfter  变更后文件
     * @return 所有实体的变更行为
     * @see #parseEntityChangeNatureFlag
     */
    List<EntityChange> parseChangeNatureFlags(SourceCode sourceCodeBefore, SourceCode sourceCodeAfter);

    /**
     * 解析指定实体的变更行为标志位掩码。参数应当仅包含该实体的完整原始内容，包括注释。
     * @param sourceCodeBefore 变更前实体代码
     * @param sourceCodeAfter 变更后实体代码
     * @return {@link ChangeNatureFlag#codeOf(EnumSet)} 位掩码
     */
    int parseEntityChangeNatureFlag(String sourceCodeBefore, String sourceCodeAfter);

    void setConfig(C config);

    C getConfig();
}
