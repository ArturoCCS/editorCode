package com.modelo.ast;

public class BinaryOpNode extends ASTNode {
    private final String operator;
    private final ASTNode left;
    private final ASTNode right;

    public BinaryOpNode(int start, int end, String operator, ASTNode left, ASTNode right) {
        super(start, end);
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    public String getOperator() { return operator; }
    public ASTNode getLeft() { return left; }
    public ASTNode getRight() { return right; }
}