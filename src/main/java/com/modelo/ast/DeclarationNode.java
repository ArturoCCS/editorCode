package com.modelo.ast;

public class DeclarationNode extends ASTNode {
    private final String type;
    private final String identifier;
    private final ASTNode initializer;

    public DeclarationNode(int start, int end, String type, String identifier, ASTNode initializer) {
        super(start, end);
        this.type = type;
        this.identifier = identifier;
        this.initializer = initializer;
    }

    public String getType() { return type; }
    public String getIdentifier() { return identifier; }
    public ASTNode getInitializer() { return initializer; }
}