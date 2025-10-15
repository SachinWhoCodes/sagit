package com.sagit.semantic;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;

public class JavaSemanticAnalyzer {

    public static class Stats {
        public int classes;
        public int interfaces_;
        public int enums_;
        public int methods;
        public int fields;

        public Stats diff(Stats other) {
            Stats d = new Stats();
            d.classes    = this.classes - other.classes;
            d.interfaces_= this.interfaces_ - other.interfaces_;
            d.enums_     = this.enums_ - other.enums_;
            d.methods    = this.methods - other.methods;
            d.fields     = this.fields - other.fields;
            return d;
        }
    }

    public Stats analyze(String source) {
        Stats s = new Stats();
        try {
            CompilationUnit cu = StaticJavaParser.parse(source);
            s.classes     = cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class,
                    n -> !n.isInterface()).size();
            s.interfaces_ = cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class,
                    com.github.javaparser.ast.body.ClassOrInterfaceDeclaration::isInterface).size();
            s.enums_      = cu.findAll(com.github.javaparser.ast.body.EnumDeclaration.class).size();
            s.methods     = cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class).size();
            s.fields      = cu.findAll(com.github.javaparser.ast.body.FieldDeclaration.class).size();
        } catch (Exception ignored) { /* fall back to zeros */ }
        return s;
    }
}
