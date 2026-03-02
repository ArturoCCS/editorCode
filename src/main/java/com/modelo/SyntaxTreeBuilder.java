package com.modelo;

import com.modelo.ast.*;
import java.util.*;

public class SyntaxTreeBuilder {
    private int nextId = 1;
    private Map<ASTNode, Integer> nodeIds = new HashMap<>();
    private List<SyntaxTreeNodeRow> rows = new ArrayList<>();

    public List<SyntaxTreeNodeRow> build(ASTNode root) {
        if (root == null) return Collections.emptyList();
        rows.clear();
        nodeIds.clear();
        nextId = 1;
        traverse(root, 0);
        return rows;
    }

    private int getId(ASTNode node) {
        return nodeIds.computeIfAbsent(node, k -> nextId++);
    }

    private void traverse(ASTNode node, int parentId) {
        int myId = getId(node);
        String tokenName = getTokenName(node);
        rows.add(new SyntaxTreeNodeRow(myId, tokenName, parentId));

        if (node instanceof ProgramNode) {
            ProgramNode pn = (ProgramNode) node;
            for (ASTNode child : pn.getStatements()) {
                traverse(child, myId);
            }
        } else if (node instanceof RecursiveDescentParser.DeclarationGroupNode) {
            RecursiveDescentParser.DeclarationGroupNode dgn = (RecursiveDescentParser.DeclarationGroupNode) node;
            for (ASTNode child : dgn.getDeclarations()) {
                traverse(child, myId);
            }
        } else if (node instanceof DeclarationNode) {
            DeclarationNode dn = (DeclarationNode) node;
            if (dn.getInitializer() != null) {
                traverse(dn.getInitializer(), myId);
            }
        } else if (node instanceof AssignmentNode) {
            AssignmentNode an = (AssignmentNode) node;
            traverse(an.getExpression(), myId);
        } else if (node instanceof ReadNode) {
        } else if (node instanceof PrintNode) {
            PrintNode pn = (PrintNode) node;
            traverse(pn.getExpression(), myId);
        } else if (node instanceof BinaryOpNode) {
            BinaryOpNode bn = (BinaryOpNode) node;
            traverse(bn.getLeft(), myId);
            traverse(bn.getRight(), myId);
        } else if (node instanceof LiteralNode) {

        } else if (node instanceof IdentifierNode) {

        }
    }

    private String getTokenName(ASTNode node) {
        if (node instanceof ProgramNode) return "Programa";
        if (node instanceof RecursiveDescentParser.DeclarationGroupNode) return "GrupoDeclaraciones";
        if (node instanceof DeclarationNode) {
            DeclarationNode dn = (DeclarationNode) node;
            return "Declaracion " + dn.getType() + " " + dn.getIdentifier();
        }
        if (node instanceof AssignmentNode) {
            AssignmentNode an = (AssignmentNode) node;
            return "Asignacion " + an.getIdentifier();
        }
        if (node instanceof ReadNode) {
            ReadNode rn = (ReadNode) node;
            return "Read " + rn.getIdentifier();
        }
        if (node instanceof PrintNode) return "Print";
        if (node instanceof BinaryOpNode) {
            BinaryOpNode bn = (BinaryOpNode) node;
            return "Operador " + bn.getOperator();
        }
        if (node instanceof LiteralNode) {
            LiteralNode ln = (LiteralNode) node;
            return "Literal " + ln.getValue();
        }
        if (node instanceof IdentifierNode) {
            IdentifierNode in = (IdentifierNode) node;
            return "ID " + in.getName();
        }
        return "Desconocido";
    }
}