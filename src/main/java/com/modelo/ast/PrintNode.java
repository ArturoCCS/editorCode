package com.modelo.ast;

public class PrintNode extends ASTNode {
    private final ASTNode expression;

    public PrintNode(int start, int end, ASTNode expression) {
        super(start, end);
        this.expression = expression;
    }

    public ASTNode getExpression() { return expression; }
}