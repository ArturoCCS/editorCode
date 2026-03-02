package com.modelo.ast;

public class ReadNode extends ASTNode {
    private final String identifier;

    public ReadNode(int start, int end, String identifier) {
        super(start, end);
        this.identifier = identifier;
    }

    public String getIdentifier() { return identifier; }
}