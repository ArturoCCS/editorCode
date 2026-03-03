package com.modelo.lr;

import com.controlador.TokenManager;
import com.modelo.SyntaxTreeNodeRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds a parse-tree by simulating shift/reduce (bottom-up LR-style) processing.
 *
 * <p>The grammar used:
 * <pre>
 * PROGRAMA    → INICIO CUERPO FINAL
 * INICIO      → START {
 * FINAL       → } END
 * CUERPO      → INSTRUCCION CUERPO | ε
 * INSTRUCCION → DECLARACION ; | ASIGNACION ; | LECTURA ; | ESCRITURA ;
 * DECLARACION → INTEGER LISTAIDS | DECIMAL LISTAIDS
 * LISTAIDS    → ELEMENTOID | LISTAIDS , ELEMENTOID
 * ELEMENTOID  → id | ASIGNACION
 * ASIGNACION  → id = E
 * LECTURA     → READ ( id )
 * ESCRITURA   → PRINT ( E )
 * E           → E + T | E - T | T
 * T           → T * F | T / F | F
 * F           → id | num | ( E )
 * </pre>
 *
 * <p>Node IDs are assigned in <em>post-order</em>, which reproduces the creation order
 * of a true shift/reduce parser:
 * <ul>
 *   <li>Terminal (token) nodes receive lower IDs because they are created first (SHIFT).</li>
 *   <li>Non-terminal nodes receive higher IDs because they are created after their
 *       RHS symbols have been placed on the stack (REDUCE).</li>
 * </ul>
 *
 * <p>Leaf node labels use the {@code TK_*} token-type names (e.g. {@code TK_START},
 * {@code TK_ID}) rather than the raw lexeme.
 *
 * <p>The root {@code PROGRAMA} node always has {@code parentId = null}, displayed
 * as {@code NULL} in the UI.
 */
public class ShiftReduceParseTreeBuilder {

    // -------------------------------------------------------------------------
    // Internal parse-tree node (IDs assigned later, in post-order)
    // -------------------------------------------------------------------------

    private static final class PNode {
        final String label;
        final List<PNode> children = new ArrayList<>();
        int id = -1;            // assigned in post-order traversal

        PNode(String label) {
            this.label = label;
        }
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private List<TokenManager.TokenEntry> tokens;
    private int pos;
    private int nextId;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Builds the shift/reduce parse tree for the given token list.
     *
     * @param tokens tokenised input produced by the Tokenizer
     * @return ordered list of {@link SyntaxTreeNodeRow}s; the root is always first;
     *         empty list on unrecoverable failure
     */
    public List<SyntaxTreeNodeRow> build(List<TokenManager.TokenEntry> tokens) {
        this.tokens = tokens;
        this.pos = 0;
        this.nextId = 1;
        try {
            PNode root = buildPROGRAMA();
            assignIds(root);
            List<SyntaxTreeNodeRow> rows = new ArrayList<>();
            flatten(root, null, rows);
            return rows;
        } catch (Exception e) {
            // Return a minimal tree so the table is never completely empty
            PNode fallback = new PNode("PROGRAMA");
            fallback.id = 1;
            List<SyntaxTreeNodeRow> rows = new ArrayList<>();
            rows.add(new SyntaxTreeNodeRow(1, "PROGRAMA", null));
            return rows;
        }
    }

    // -------------------------------------------------------------------------
    // ID assignment: post-order → tokens (leaves) get lower IDs than their parents
    // -------------------------------------------------------------------------

    private void assignIds(PNode node) {
        for (PNode child : node.children) {
            assignIds(child);
        }
        node.id = nextId++;
    }

    // -------------------------------------------------------------------------
    // Flatten: pre-order display with post-order IDs
    // -------------------------------------------------------------------------

    private void flatten(PNode node, Integer parentId, List<SyntaxTreeNodeRow> rows) {
        rows.add(new SyntaxTreeNodeRow(node.id, node.label, parentId));
        for (PNode child : node.children) {
            flatten(child, node.id, rows);
        }
    }

    // -------------------------------------------------------------------------
    // Token helpers
    // -------------------------------------------------------------------------

    private boolean peek(String tokenType) {
        return pos < tokens.size() && tokens.get(pos).token().equals(tokenType);
    }

    private boolean peekAhead(int offset, String tokenType) {
        int idx = pos + offset;
        return idx < tokens.size() && tokens.get(idx).token().equals(tokenType);
    }

    /** Shift the current token (if it matches {@code tokenType}) and return a leaf node. */
    private PNode shift(String tokenType) {
        if (!peek(tokenType)) return null;
        TokenManager.TokenEntry t = tokens.get(pos++);
        return new PNode(t.token());   // use TK_* name as label
    }

    /** Shift the current token unconditionally and return a leaf node. */
    private PNode shiftAny() {
        if (pos >= tokens.size()) return null;
        TokenManager.TokenEntry t = tokens.get(pos++);
        return new PNode(t.token());
    }

    /** Create a non-terminal node and adopt the given children. */
    private PNode reduce(String symbol, PNode... children) {
        PNode node = new PNode(symbol);
        for (PNode child : children) {
            if (child != null) node.children.add(child);
        }
        return node;
    }

    // -------------------------------------------------------------------------
    // Grammar productions
    // -------------------------------------------------------------------------

    /** PROGRAMA → INICIO CUERPO FINAL */
    private PNode buildPROGRAMA() {
        PNode inicio  = buildINICIO();
        PNode cuerpo  = buildCUERPO();
        PNode final_  = buildFINAL();
        PNode prog = new PNode("PROGRAMA");
        if (inicio != null)  prog.children.add(inicio);
        if (cuerpo != null)  prog.children.add(cuerpo);
        if (final_ != null)  prog.children.add(final_);
        return prog;
    }

    /** INICIO → START { */
    private PNode buildINICIO() {
        PNode start  = shift("TK_START");
        PNode lbrace = shift("TK_{");
        PNode node = new PNode("INICIO");
        if (start  != null) node.children.add(start);
        if (lbrace != null) node.children.add(lbrace);
        return node;
    }

    /** FINAL → } END */
    private PNode buildFINAL() {
        PNode rbrace = shift("TK_}");
        PNode end    = shift("TK_END");
        PNode node = new PNode("FINAL");
        if (rbrace != null) node.children.add(rbrace);
        if (end    != null) node.children.add(end);
        return node;
    }

    /**
     * CUERPO → INSTRUCCION CUERPO | ε
     * FIRST(INSTRUCCION) = { TK_tipoEntero, TK_tipoDecimal, TK_ID, TK_READ, TK_PRINT }
     */
    private PNode buildCUERPO() {
        PNode node = new PNode("CUERPO");
        if (peek("TK_tipoEntero") || peek("TK_tipoDecimal")
                || peek("TK_ID") || peek("TK_READ") || peek("TK_PRINT")) {
            PNode instr = buildINSTRUCCION();
            if (instr != null) node.children.add(instr);
            PNode rest  = buildCUERPO();
            if (rest  != null) node.children.add(rest);
        }
        // ε production – CUERPO node with no children represents empty body
        return node;
    }

    /** INSTRUCCION → DECLARACION ; | ASIGNACION ; | LECTURA ; | ESCRITURA ; */
    private PNode buildINSTRUCCION() {
        PNode node = new PNode("INSTRUCCION");
        if (peek("TK_tipoEntero") || peek("TK_tipoDecimal")) {
            PNode decl = buildDECLARACION();
            if (decl != null) node.children.add(decl);
            PNode semi = shift("TK_;");
            if (semi != null) node.children.add(semi);
        } else if (peek("TK_ID")) {
            PNode asig = buildASIGNACION();
            if (asig != null) node.children.add(asig);
            PNode semi = shift("TK_;");
            if (semi != null) node.children.add(semi);
        } else if (peek("TK_READ")) {
            PNode lect = buildLECTURA();
            if (lect != null) node.children.add(lect);
            PNode semi = shift("TK_;");
            if (semi != null) node.children.add(semi);
        } else if (peek("TK_PRINT")) {
            PNode escr = buildESCRITURA();
            if (escr != null) node.children.add(escr);
            PNode semi = shift("TK_;");
            if (semi != null) node.children.add(semi);
        }
        return node;
    }

    /** DECLARACION → INTEGER LISTAIDS | DECIMAL LISTAIDS */
    private PNode buildDECLARACION() {
        PNode node = new PNode("DECLARACION");
        PNode tipo = null;
        if (peek("TK_tipoEntero"))  tipo = shift("TK_tipoEntero");
        else if (peek("TK_tipoDecimal")) tipo = shift("TK_tipoDecimal");
        if (tipo != null) node.children.add(tipo);
        PNode lista = buildLISTAIDS();
        if (lista != null) node.children.add(lista);
        return node;
    }

    /**
     * LISTAIDS → ELEMENTOID | LISTAIDS , ELEMENTOID   (left-recursive)
     *
     * Parsed iteratively; the resulting tree encodes the left-recursive structure
     * by nesting LISTAIDS nodes to the left, as a true LR parser would produce.
     */
    private PNode buildLISTAIDS() {
        // First element
        PNode elem = buildELEMENTOID();
        PNode current = new PNode("LISTAIDS");
        if (elem != null) current.children.add(elem);

        // Each , ELEMENTOID extends the list left-recursively
        while (peek("TK_,")) {
            PNode comma   = shift("TK_,");
            PNode nextElem = buildELEMENTOID();
            PNode newList = new PNode("LISTAIDS");
            newList.children.add(current);          // left child: previous LISTAIDS
            if (comma    != null) newList.children.add(comma);
            if (nextElem != null) newList.children.add(nextElem);
            current = newList;
        }
        return current;
    }

    /**
     * ELEMENTOID → id | ASIGNACION
     * One token of lookahead: "id =" → ASIGNACION; bare "id" → id leaf.
     */
    private PNode buildELEMENTOID() {
        PNode node = new PNode("ELEMENTOID");
        if (peek("TK_ID")) {
            boolean isAssignment = peekAhead(1, "TK_=");
            if (isAssignment) {
                PNode asig = buildASIGNACION();
                if (asig != null) node.children.add(asig);
            } else {
                PNode id = shift("TK_ID");
                if (id != null) node.children.add(id);
            }
        }
        return node;
    }

    /** ASIGNACION → id = E */
    private PNode buildASIGNACION() {
        PNode id   = shift("TK_ID");
        PNode eq   = shift("TK_=");
        PNode expr = buildE();
        PNode node = new PNode("ASIGNACION");
        if (id   != null) node.children.add(id);
        if (eq   != null) node.children.add(eq);
        if (expr != null) node.children.add(expr);
        return node;
    }

    /** LECTURA → READ ( id ) */
    private PNode buildLECTURA() {
        PNode read = shift("TK_READ");
        PNode lp   = shift("TK_(");
        PNode id   = shift("TK_ID");
        PNode rp   = shift("TK_)");
        PNode node = new PNode("LECTURA");
        if (read != null) node.children.add(read);
        if (lp   != null) node.children.add(lp);
        if (id   != null) node.children.add(id);
        if (rp   != null) node.children.add(rp);
        return node;
    }

    /** ESCRITURA → PRINT ( E ) */
    private PNode buildESCRITURA() {
        PNode print = shift("TK_PRINT");
        PNode lp    = shift("TK_(");
        PNode expr  = buildE();
        PNode rp    = shift("TK_)");
        PNode node  = new PNode("ESCRITURA");
        if (print != null) node.children.add(print);
        if (lp    != null) node.children.add(lp);
        if (expr  != null) node.children.add(expr);
        if (rp    != null) node.children.add(rp);
        return node;
    }

    /**
     * E → E + T | E - T | T   (left-recursive, parsed iteratively)
     *
     * In post-order ID assignment this mirrors the shift/reduce sequence:
     * the innermost T is reduced first, then each E wraps the previous result.
     */
    private PNode buildE() {
        // Reduce: T → E (base case)
        PNode t = buildT();
        PNode current = new PNode("E");
        if (t != null) current.children.add(t);

        while (peek("TK_+") || peek("TK_-")) {
            PNode op    = shiftAny();
            PNode nextT = buildT();
            PNode newE  = new PNode("E");
            newE.children.add(current);             // E (previously reduced)
            if (op    != null) newE.children.add(op);
            if (nextT != null) newE.children.add(nextT);
            current = newE;
        }
        return current;
    }

    /**
     * T → T * F | T / F | F   (left-recursive, parsed iteratively)
     */
    private PNode buildT() {
        PNode f = buildF();
        PNode current = new PNode("T");
        if (f != null) current.children.add(f);

        while (peek("TK_*") || peek("TK_/")) {
            PNode op   = shiftAny();
            PNode nextF = buildF();
            PNode newT  = new PNode("T");
            newT.children.add(current);             // T (previously reduced)
            if (op    != null) newT.children.add(op);
            if (nextF != null) newT.children.add(nextF);
            current = newT;
        }
        return current;
    }

    /** F → id | num | ( E ) */
    private PNode buildF() {
        PNode node = new PNode("F");
        if (peek("TK_ID")) {
            PNode id = shift("TK_ID");
            if (id != null) node.children.add(id);
        } else if (peek("TK_ENT") || peek("TK_DEC")) {
            PNode num = shiftAny();
            if (num != null) node.children.add(num);
        } else if (peek("TK_(")) {
            PNode lp   = shift("TK_(");
            PNode expr = buildE();
            PNode rp   = shift("TK_)");
            if (lp   != null) node.children.add(lp);
            if (expr != null) node.children.add(expr);
            if (rp   != null) node.children.add(rp);
        }
        return node;
    }
}
