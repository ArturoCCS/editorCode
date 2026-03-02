package com.modelo.parser;

import com.controlador.TokenManager;
import com.modelo.SyntaxTreeNodeRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds a parse tree (derivation tree) from a token list following the grammar:
 *
 * <pre>
 * PROGRAMA  → INICIO CUERPO FINAL
 * INICIO    → START {
 * FINAL     → } END
 * CUERPO    → INSTRUCCION CUERPO | ε
 * INSTRUCCION → DECLARACION ; | ASIGNACION ; | LECTURA ; | ESCRITURA ;
 * DECLARACION → INTEGER LISTAIDS | DECIMAL LISTAIDS
 * LISTAIDS  → ELEMENTOID | ELEMENTOID , LISTAIDS
 * ELEMENTOID → id | ASIGNACION
 * ASIGNACION → id = E
 * LECTURA   → READ ( id )
 * ESCRITURA → PRINT ( E )
 * E         → E + T | E - T | T
 * T         → T * F | T / F | F
 * F         → id | num | ( E )
 * </pre>
 *
 * Returns a list of {@link SyntaxTreeNodeRow} where the root node (PROGRAMA) has parentId = 0.
 */
public class ParseTreeBuilder {

    /** Internal parse-tree node. */
    private static final class PNode {
        int id;
        final String label;
        int parentId;
        final List<PNode> children = new ArrayList<>();

        PNode(int id, String label, int parentId) {
            this.id = id;
            this.label = label;
            this.parentId = parentId;
        }
    }

    /**
     * When {@code true} (Format A), leaf nodes for identifiers are labelled
     * {@code id(<lexema>)} and for integer/decimal literals {@code num(<lexema>)}.
     * When {@code false} (Format B, default), the raw lexeme is used as-is.
     */
    public static final boolean DEFAULT_WRAP_ID_AND_NUM = false;

    private List<TokenManager.TokenEntry> tokens;
    private int pos;
    private int nextId;
    private boolean wrapIdAndNumInSyntaxTree;

    /**
     * Builds the parse tree using the default token display format (Format B: raw lexemes).
     *
     * @param tokens tokenised input (as produced by {@link com.modelo.Tokenizer})
     * @return ordered list of tree rows suitable for the syntax-tree table; empty on failure
     */
    public List<SyntaxTreeNodeRow> build(List<TokenManager.TokenEntry> tokens) {
        return build(tokens, DEFAULT_WRAP_ID_AND_NUM);
    }

    /**
     * Builds the parse tree with an explicit token display format.
     *
     * @param tokens         tokenised input (as produced by {@link com.modelo.Tokenizer})
     * @param wrapIdAndNum   {@code true} for Format A ({@code id(…)}/{@code num(…)}),
     *                       {@code false} for Format B (raw lexeme)
     * @return ordered list of tree rows suitable for the syntax-tree table; empty on failure
     */
    public List<SyntaxTreeNodeRow> build(List<TokenManager.TokenEntry> tokens, boolean wrapIdAndNum) {
        this.tokens = tokens;
        this.pos = 0;
        this.nextId = 1;
        this.wrapIdAndNumInSyntaxTree = wrapIdAndNum;
        try {
            PNode root = parsePROGRAMA();
            List<SyntaxTreeNodeRow> rows = new ArrayList<>();
            flatten(root, rows);
            return rows;
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Tree helpers
    // -------------------------------------------------------------------------

    private void flatten(PNode node, List<SyntaxTreeNodeRow> rows) {
        rows.add(new SyntaxTreeNodeRow(node.id, node.label, node.parentId));
        for (PNode child : node.children) {
            flatten(child, rows);
        }
    }

    private PNode newNode(String label, int parentId) {
        return new PNode(nextId++, label, parentId);
    }

    private boolean peek(String tokenType) {
        return pos < tokens.size() && tokens.get(pos).token().equals(tokenType);
    }

    /** Consume current token and return a leaf node, or {@code null} if at end. */
    private PNode consume(int parentId) {
        if (pos >= tokens.size()) return null;
        TokenManager.TokenEntry t = tokens.get(pos++);
        return new PNode(nextId++, leafLabel(t), parentId);
    }

    /**
     * Returns the display label for a leaf token node.
     * In Format A ({@code wrapIdAndNumInSyntaxTree=true}): identifiers become
     * {@code id(<lexema>)}, integer/decimal literals become {@code num(<lexema>)}.
     * In Format B (default): the raw lexeme is returned unchanged.
     */
    private String leafLabel(TokenManager.TokenEntry t) {
        if (wrapIdAndNumInSyntaxTree) {
            return switch (t.token()) {
                case "TK_ID"            -> "id("  + t.lexema() + ")";
                case "TK_ENT", "TK_DEC" -> "num(" + t.lexema() + ")";
                default                 -> t.lexema();
            };
        }
        return t.lexema();
    }

    /** Consume current token only if its type matches; return leaf or {@code null}. */
    private PNode match(String tokenType, int parentId) {
        if (peek(tokenType)) return consume(parentId);
        return null;
    }

    // -------------------------------------------------------------------------
    // Grammar rules
    // -------------------------------------------------------------------------

    /** PROGRAMA → INICIO CUERPO FINAL */
    private PNode parsePROGRAMA() {
        PNode node = newNode("PROGRAMA", 0);
        PNode inicio = parseINICIO(node.id);
        if (inicio != null) node.children.add(inicio);
        PNode cuerpo = parseCUERPO(node.id);
        if (cuerpo != null) node.children.add(cuerpo);
        PNode fin = parseFINAL(node.id);
        if (fin != null) node.children.add(fin);
        return node;
    }

    /** INICIO → START { */
    private PNode parseINICIO(int parentId) {
        PNode node = newNode("INICIO", parentId);
        PNode start = match("TK_START", node.id);
        if (start != null) node.children.add(start);
        PNode lbrace = match("TK_{", node.id);
        if (lbrace != null) node.children.add(lbrace);
        return node;
    }

    /** FINAL → } END */
    private PNode parseFINAL(int parentId) {
        PNode node = newNode("FINAL", parentId);
        PNode rbrace = match("TK_}", node.id);
        if (rbrace != null) node.children.add(rbrace);
        PNode end = match("TK_END", node.id);
        if (end != null) node.children.add(end);
        return node;
    }

    /**
     * CUERPO → INSTRUCCION CUERPO | ε
     * First(INSTRUCCION) = { INTEGER, DECIMAL, id, READ, PRINT }
     */
    private PNode parseCUERPO(int parentId) {
        PNode node = newNode("CUERPO", parentId);
        if (peek("TK_tipoEntero") || peek("TK_tipoDecimal")
                || peek("TK_ID") || peek("TK_READ") || peek("TK_PRINT")) {
            PNode instr = parseINSTRUCCION(node.id);
            if (instr != null) node.children.add(instr);
            PNode rest = parseCUERPO(node.id);
            if (rest != null) node.children.add(rest);
        }
        // ε production – node has no children; still included in tree
        return node;
    }

    /** INSTRUCCION → DECLARACION ; | ASIGNACION ; | LECTURA ; | ESCRITURA ; */
    private PNode parseINSTRUCCION(int parentId) {
        PNode node = newNode("INSTRUCCION", parentId);
        if (peek("TK_tipoEntero") || peek("TK_tipoDecimal")) {
            PNode decl = parseDECLARACION(node.id);
            if (decl != null) node.children.add(decl);
            PNode semi = match("TK_;", node.id);
            if (semi != null) node.children.add(semi);
        } else if (peek("TK_ID")) {
            PNode asig = parseASIGNACION(node.id);
            if (asig != null) node.children.add(asig);
            PNode semi = match("TK_;", node.id);
            if (semi != null) node.children.add(semi);
        } else if (peek("TK_READ")) {
            PNode lect = parseLECTURA(node.id);
            if (lect != null) node.children.add(lect);
            PNode semi = match("TK_;", node.id);
            if (semi != null) node.children.add(semi);
        } else if (peek("TK_PRINT")) {
            PNode escr = parseESCRITURA(node.id);
            if (escr != null) node.children.add(escr);
            PNode semi = match("TK_;", node.id);
            if (semi != null) node.children.add(semi);
        }
        return node;
    }

    /** DECLARACION → INTEGER LISTAIDS | DECIMAL LISTAIDS */
    private PNode parseDECLARACION(int parentId) {
        PNode node = newNode("DECLARACION", parentId);
        PNode tipo = null;
        if (peek("TK_tipoEntero")) {
            tipo = match("TK_tipoEntero", node.id);
        } else if (peek("TK_tipoDecimal")) {
            tipo = match("TK_tipoDecimal", node.id);
        }
        if (tipo != null) node.children.add(tipo);
        PNode lista = parseLISTAIDS(node.id);
        if (lista != null) node.children.add(lista);
        return node;
    }

    /** LISTAIDS → ELEMENTOID | ELEMENTOID , LISTAIDS */
    private PNode parseLISTAIDS(int parentId) {
        PNode node = newNode("LISTAIDS", parentId);
        PNode elem = parseELEMENTOID(node.id);
        if (elem != null) node.children.add(elem);
        if (peek("TK_,")) {
            PNode comma = match("TK_,", node.id);
            if (comma != null) node.children.add(comma);
            PNode rest = parseLISTAIDS(node.id);
            if (rest != null) node.children.add(rest);
        }
        return node;
    }

    /**
     * ELEMENTOID → id | ASIGNACION
     * Use one token of lookahead: "id =" means ASIGNACION, bare "id" is just the identifier.
     */
    private PNode parseELEMENTOID(int parentId) {
        PNode node = newNode("ELEMENTOID", parentId);
        if (peek("TK_ID")) {
            boolean isAssignment = pos + 1 < tokens.size()
                    && tokens.get(pos + 1).token().equals("TK_=");
            if (isAssignment) {
                PNode asig = parseASIGNACION(node.id);
                if (asig != null) node.children.add(asig);
            } else {
                PNode id = match("TK_ID", node.id);
                if (id != null) node.children.add(id);
            }
        }
        return node;
    }

    /** ASIGNACION → id = E */
    private PNode parseASIGNACION(int parentId) {
        PNode node = newNode("ASIGNACION", parentId);
        PNode id = match("TK_ID", node.id);
        if (id != null) node.children.add(id);
        PNode eq = match("TK_=", node.id);
        if (eq != null) node.children.add(eq);
        PNode expr = parseE(node.id);
        if (expr != null) node.children.add(expr);
        return node;
    }

    /** LECTURA → READ ( id ) */
    private PNode parseLECTURA(int parentId) {
        PNode node = newNode("LECTURA", parentId);
        PNode read = match("TK_READ", node.id);
        if (read != null) node.children.add(read);
        PNode lp = match("TK_(", node.id);
        if (lp != null) node.children.add(lp);
        PNode id = match("TK_ID", node.id);
        if (id != null) node.children.add(id);
        PNode rp = match("TK_)", node.id);
        if (rp != null) node.children.add(rp);
        return node;
    }

    /** ESCRITURA → PRINT ( E ) */
    private PNode parseESCRITURA(int parentId) {
        PNode node = newNode("ESCRITURA", parentId);
        PNode print = match("TK_PRINT", node.id);
        if (print != null) node.children.add(print);
        PNode lp = match("TK_(", node.id);
        if (lp != null) node.children.add(lp);
        PNode expr = parseE(node.id);
        if (expr != null) node.children.add(expr);
        PNode rp = match("TK_)", node.id);
        if (rp != null) node.children.add(rp);
        return node;
    }

    /**
     * E → E + T | E - T | T
     *
     * Left recursion is handled iteratively: parse T first, then for each
     * additive operator wrap the accumulated E in a new E node so the resulting
     * parse tree correctly reflects the left-recursive production.
     */
    private PNode parseE(int parentId) {
        PNode left = newNode("E", parentId);
        PNode t = parseT(left.id);
        if (t != null) left.children.add(t);

        while (peek("TK_+") || peek("TK_-")) {
            PNode newE = newNode("E", parentId);
            left.parentId = newE.id;
            newE.children.add(left);

            PNode op = consume(newE.id);
            if (op != null) newE.children.add(op);

            PNode t2 = parseT(newE.id);
            if (t2 != null) newE.children.add(t2);

            left = newE;
        }

        left.parentId = parentId;
        return left;
    }

    /**
     * T → T * F | T / F | F
     *
     * Same iterative left-recursion elimination as {@link #parseE}.
     */
    private PNode parseT(int parentId) {
        PNode left = newNode("T", parentId);
        PNode f = parseF(left.id);
        if (f != null) left.children.add(f);

        while (peek("TK_*") || peek("TK_/")) {
            PNode newT = newNode("T", parentId);
            left.parentId = newT.id;
            newT.children.add(left);

            PNode op = consume(newT.id);
            if (op != null) newT.children.add(op);

            PNode f2 = parseF(newT.id);
            if (f2 != null) newT.children.add(f2);

            left = newT;
        }

        left.parentId = parentId;
        return left;
    }

    /** F → id | num | ( E ) */
    private PNode parseF(int parentId) {
        PNode node = newNode("F", parentId);
        if (peek("TK_ID")) {
            PNode id = match("TK_ID", node.id);
            if (id != null) node.children.add(id);
        } else if (peek("TK_ENT") || peek("TK_DEC")) {
            PNode num = consume(node.id);
            if (num != null) node.children.add(num);
        } else if (peek("TK_(")) {
            PNode lp = match("TK_(", node.id);
            if (lp != null) node.children.add(lp);
            PNode expr = parseE(node.id);
            if (expr != null) node.children.add(expr);
            PNode rp = match("TK_)", node.id);
            if (rp != null) node.children.add(rp);
        }
        return node;
    }
}
