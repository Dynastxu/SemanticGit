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
     * 解析指定实体的变更行为标志位掩码，支持多标志组合（如 REFACTOR | DOCS）。
     * 每个 changelog 对应一个实体（类/方法），应独立调用此方法获取其专属的 flag 组合。
     *
     * @param sourceCodeBefore 变更前文件
     * @param sourceCodeAfter  变更后文件
     * @param entityName       实体全限定名，用于定位具体类/方法分析其变更性质
     * @return {@link ChangeNatureFlag#toCode(EnumSet)} 位掩码
     */
    int parseChangeNatureFlag(SourceCode sourceCodeBefore, SourceCode sourceCodeAfter, String entityName);

    void setConfig(C config);

    C getConfig();
}
