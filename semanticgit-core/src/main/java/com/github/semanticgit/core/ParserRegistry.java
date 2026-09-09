package com.github.semanticgit.core;

import com.github.semanticgit.common.entity.DataQuality;
import com.github.semanticgit.common.entity.EntityLanguage;
import com.github.semanticgit.parser.java.JavaLanguageParser;
import com.github.semanticgit.parser.java.JavaParserConfig;
import com.github.semanticgit.parser.java.api.LanguageParser;
import com.github.semanticgit.parser.java.api.ParserConfig;
import com.github.semanticgit.parser.java.api.ParsingResult;
import com.github.semanticgit.parser.java.api.SourceCode;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class ParserRegistry {
    private final Map<EntityLanguage, LanguageParser<?>> parsers = new EnumMap<>(EntityLanguage.class);

    public ParserRegistry() {
        parsers.put(EntityLanguage.JAVA, new JavaLanguageParser());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public ParsingResult parse(SourceCode sourceCode) {
        LanguageParser parser = (LanguageParser) parsers.get(sourceCode.getLanguage());
        if (parser == null) {
            return ParsingResult.builder()
                    .entities(Collections.emptyList())
                    .quality(DataQuality.FILE)
                    .qualityRemark("NO_PARSER_FOR_" + sourceCode.getLanguage())
                    .parseDurationMs(0)
                    .build();
        }
        return parser.parse(sourceCode, getDefaultConfig(sourceCode.getLanguage()));
    }

    private ParserConfig getDefaultConfig(EntityLanguage language) {
        return switch (language) {
            case JAVA -> JavaParserConfig.builder().build();
            default -> null;
        };
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
