package com.modelo.ast;

import java.util.List;

public class ProgramNode extends ASTNode {
    private final List<ASTNode> statements;

    public ProgramNode(int start, int end, List<ASTNode> statements) {
        super(start, end);
        this.statements = statements;
    }

    public List<ASTNode> getStatements() { return statements; }
}