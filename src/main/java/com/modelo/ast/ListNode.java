package com.modelo.ast;
import java.util.List;

public class ListNode extends ASTNode {
    private final List<ASTNode> elements;
    public ListNode(int start, int end, List<ASTNode> elements) {
        super(start, end);
        this.elements = elements;
    }
    public List<ASTNode> getElements() { return elements; }
}