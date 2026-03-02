package com.modelo.ast;

public class AssignmentNode extends ASTNode {
    private final String identifier;
    private final ASTNode expression;

    public AssignmentNode(int start, int end, String identifier, ASTNode expression) {
        super(start, end);
        this.identifier = identifier;
        this.expression = expression;
    }

    public String getIdentifier() { return identifier; }
    public ASTNode getExpression() { return expression; }
}