package com.modelo.ast;

public class IdentifierNode extends ASTNode {
    private final String name;

    public IdentifierNode(int start, int end, String name) {
        super(start, end);
        this.name = name;
    }

    public String getName() { return name; }
}