package com.modelo.ast;

public abstract class ASTNode {
    protected int start;
    protected int end;

    public ASTNode(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public int getStart() { return start; }
    public int getEnd() { return end; }
}