import entity.EntityLanguage;

public interface LanguageParser<C extends ParserConfig> {
    /**
     * 解析源码文件，提取类、方法等实体信息。
     * 实现者必须保证：无论如何都不抛出运行时异常，必须返回 ParsingResult。
     */
    ParsingResult parse(SourceCode sourceCode, C config);

    /**
     * 声明该解析器支持的语言类型，用于工厂模式自动路由。
     */
    EntityLanguage getSupportedLanguage();
}
