package com.github.semanticgit.parser.java.api;

import com.github.semanticgit.common.entity.ChangeNatureFlag;
import com.github.semanticgit.common.entity.EntityLanguage;

import java.util.EnumSet;

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
     * 解析源码文件的变更行为标志
     * @return {@link ChangeNatureFlag#toCode(EnumSet)}
     */
    int parseChangeNatureFlag(SourceCode sourceCodeBefore, SourceCode sourceCodeAfter);

    void setConfig(C config);
}
