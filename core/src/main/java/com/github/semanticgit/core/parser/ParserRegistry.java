package com.github.semanticgit.core.parser;

import com.github.semanticgit.common.entity.EntityLanguage;
import com.github.semanticgit.parser.java.JavaLanguageParser;
import com.github.semanticgit.parser.java.JavaParserConfig;
import com.github.semanticgit.parser.java.api.LanguageParser;
import com.github.semanticgit.parser.java.api.ParserConfig;
import org.jspecify.annotations.NonNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

public class ParserRegistry {
    private static final Map<EntityLanguage, Supplier<LanguageParser<?>>> parsers = new EnumMap<>(EntityLanguage.class);
    private static final Map<EntityLanguage, Supplier<? extends ParserConfig>> configs = new EnumMap<>(EntityLanguage.class);

    static {
        registerParser(EntityLanguage.JAVA, JavaLanguageParser::new, () -> JavaParserConfig.builder().build());
    }

    public static void registerParser(EntityLanguage language, Supplier<LanguageParser<? extends ParserConfig>> parser, Supplier<? extends ParserConfig> defaultConfig) {
        parsers.put(language, parser);
        configs.put(language, defaultConfig);
    }

    public static @NonNull LanguageParser<?> getParser(EntityLanguage language) {
        LanguageParser parser = parsers.get(language).get();
        parser.setConfig(configs.get(language).get());
        return parser;
    }

    public static <C extends ParserConfig> @NonNull LanguageParser<C> getParser(EntityLanguage language, C config) {
        LanguageParser parser = getParser(language);
        parser.setConfig(config);
        return parser;
    }

    public static EntityLanguage detectLanguage(String filePath) {
        if (filePath == null) {
            return null;
        }
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".java")) {
            return EntityLanguage.JAVA;
        }
        if (lower.endsWith(".py")) {
            return EntityLanguage.PYTHON;
        }
        if (lower.endsWith(".js")) {
            return EntityLanguage.JS;
        }
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) {
            return EntityLanguage.TS;
        }
        return null;
    }
}
