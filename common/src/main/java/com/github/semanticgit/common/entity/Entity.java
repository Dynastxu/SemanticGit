package com.github.semanticgit.common.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Entity {
    private Long id;
    /**
     * 全局唯一全限定名（跨语言规范）
     * <p>
     * Java: com.example.Service#process()<p>
     * Python: my_module.MyClass.my_method<p>
     * JS/TS: src/util.ts#formatDate
     */
    private String name;
    private EntityLanguage language;
    private EntityKind kind;
    /**
     * 所属的父实体ID（仅用于内存中的层级构建，非持久化字段）
     * <p>
     * 例如：方法所属的类ID
     */
    private Entity parent;
    /**
     * 结构化签名，用于跨文件重构匹配。
     * <p>
     * CLASS:  "SuperName:Interface1,Interface2:fieldCount:methodSig1;methodSig2"<p>
     * METHOD: "ReturnType:Param1,Param2:bodyHash"
     */
    private String signature;
}
