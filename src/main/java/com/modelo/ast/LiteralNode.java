package com.modelo.ast;

public class LiteralNode extends ASTNode {
    private final String value;
    private final boolean isInteger;

    public LiteralNode(int start, int end, String value, boolean isInteger) {
        super(start, end);
        this.value = value;
        this.isInteger = isInteger;
    }

    public String getValue() { return value; }
    public boolean isInteger() { return isInteger; }
}