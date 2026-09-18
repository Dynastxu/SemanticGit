package com.github.semanticgit.parser.java.api;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Deprecated
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ParserConfig {
    /**
     * 单文件最大解析超时（毫秒）
     */
    @Builder.Default
    private long timeoutMs = 5000;
}
