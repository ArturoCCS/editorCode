package com.modelo.lr;

import com.controlador.TokenManager;
import com.modelo.SyntaxTreeNodeRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShiftReduceParseTreeBuilder {



    private static final class PNode {
        final String label;
        final List<PNode> children = new ArrayList<>();
        int id = -1;

        PNode(String label) {
            this.label = label;
        }
    }


    private List<TokenManager.TokenEntry> tokens;
    private int pos;
    private int nextId;



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
            PNode fallback = new PNode("PROGRAMA");
            fallback.id = 1;
            List<SyntaxTreeNodeRow> rows = new ArrayList<>();
            rows.add(new SyntaxTreeNodeRow(1, "PROGRAMA", null));
            return rows;
        }
    }

    private void assignIds(PNode node) {
        for (PNode child : node.children) {
            assignIds(child);
        }
        node.id = nextId++;
    }

    private void flatten(PNode node, Integer parentId, List<SyntaxTreeNodeRow> rows) {
        rows.add(new SyntaxTreeNodeRow(node.id, node.label, parentId));
        for (PNode child : node.children) {
            flatten(child, node.id, rows);
        }
    }


    private boolean peek(String tokenType) {
        return pos < tokens.size() && tokens.get(pos).token().equals(tokenType);
    }

    private boolean peekAhead(int offset, String tokenType) {
        int idx = pos + offset;
        return idx < tokens.size() && tokens.get(idx).token().equals(tokenType);
    }


    private PNode shift(String tokenType) {
        if (!peek(tokenType)) return null;
        TokenManager.TokenEntry t = tokens.get(pos++);
        return new PNode(t.token());
    }

    private PNode shiftAny() {
        if (pos >= tokens.size()) return null;
        TokenManager.TokenEntry t = tokens.get(pos++);
        return new PNode(t.token());
    }

    private PNode reduce(String symbol, PNode... children) {
        PNode node = new PNode(symbol);
        for (PNode child : children) {
            if (child != null) node.children.add(child);
        }
        return node;
    }

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

    private PNode buildINICIO() {
        PNode start  = shift("TK_START");
        PNode lbrace = shift("TK_{");
        PNode node = new PNode("INICIO");
        if (start  != null) node.children.add(start);
        if (lbrace != null) node.children.add(lbrace);
        return node;
    }

    private PNode buildFINAL() {
        PNode rbrace = shift("TK_}");
        PNode end    = shift("TK_END");
        PNode node = new PNode("FINAL");
        if (rbrace != null) node.children.add(rbrace);
        if (end    != null) node.children.add(end);
        return node;
    }


    private PNode buildCUERPO() {
        PNode node = new PNode("CUERPO");
        PNode lista = buildLISTA_INSTRUCCIONES();
        if (lista != null) node.children.add(lista);
        return node;
    }

    private PNode buildLISTA_INSTRUCCIONES() {
        PNode current = new PNode("LISTA_INSTRUCCIONES");
        if (peek("TK_tipoEntero") || peek("TK_tipoDecimal")
                || peek("TK_ID") || peek("TK_READ") || peek("TK_PRINT")) {
            PNode instr = buildINSTRUCCION();
            if (instr != null) current.children.add(instr);

            while (peek("TK_tipoEntero") || peek("TK_tipoDecimal")
                    || peek("TK_ID") || peek("TK_READ") || peek("TK_PRINT")) {
                PNode nextInstr = buildINSTRUCCION();
                PNode newList = new PNode("LISTA_INSTRUCCIONES");
                newList.children.add(current);
                if (nextInstr != null) newList.children.add(nextInstr);
                current = newList;
            }
        }

        return current;
    }

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


    private PNode buildLISTAIDS() {

        PNode elem = buildELEMENTOID();
        PNode current = new PNode("LISTAIDS");
        if (elem != null) current.children.add(elem);

        while (peek("TK_,")) {
            PNode comma   = shift("TK_,");
            PNode nextElem = buildELEMENTOID();
            PNode newList = new PNode("LISTAIDS");
            newList.children.add(current);
            if (comma    != null) newList.children.add(comma);
            if (nextElem != null) newList.children.add(nextElem);
            current = newList;
        }
        return current;
    }

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


    private PNode buildE() {

        PNode t = buildT();
        PNode current = new PNode("E");
        if (t != null) current.children.add(t);

        while (peek("TK_+") || peek("TK_-")) {
            PNode op    = shiftAny();
            PNode nextT = buildT();
            PNode newE  = new PNode("E");
            newE.children.add(current);
            if (op    != null) newE.children.add(op);
            if (nextT != null) newE.children.add(nextT);
            current = newE;
        }
        return current;
    }

    private PNode buildT() {
        PNode f = buildF();
        PNode current = new PNode("T");
        if (f != null) current.children.add(f);

        while (peek("TK_*") || peek("TK_/")) {
            PNode op   = shiftAny();
            PNode nextF = buildF();
            PNode newT  = new PNode("T");
            newT.children.add(current);
            if (op    != null) newT.children.add(op);
            if (nextF != null) newT.children.add(nextF);
            current = newT;
        }
        return current;
    }

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
