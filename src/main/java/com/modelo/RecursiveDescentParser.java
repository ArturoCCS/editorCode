package com.modelo;

import com.controlador.TokenManager;
import com.modelo.ast.*;
import java.util.*;

public class RecursiveDescentParser {

    private List<TokenManager.TokenEntry> tokens;
    private int indice;
    private final List<ParseError> errores = new ArrayList<>();
    private TokenManager.TokenEntry ultimoTokenConsumido;

    private static final Set<String> FOLLOW_PROGRAMA = Set.of("EOF");
    private static final Set<String> FOLLOW_CUERPO = Set.of("TK_}");
    private static final Set<String> FOLLOW_DECLARACION = Set.of("TK_;", "TK_tipoEntero", "TK_tipoDecimal", "TK_ID", "TK_READ", "TK_PRINT", "TK_}");
    private static final Set<String> FOLLOW_LISTA_IDENT = Set.of("TK_;", "TK_,");
    private static final Set<String> FOLLOW_SENTENCIA = Set.of("TK_;", "TK_ID", "TK_READ", "TK_PRINT", "TK_}");
    private static final Set<String> FOLLOW_EXPRESION = Set.of("TK_;", "TK_)", "TK_,", "TK_+", "TK_-", "TK_*", "TK_/", "TK_=", "TK_}", "TK_END");
    private static final Set<String> FOLLOW_TERMINO = FOLLOW_EXPRESION;
    private static final Set<String> FOLLOW_FACTOR = FOLLOW_EXPRESION;

    public static class ParseError {
        public final int start;
        public final int end;
        public final String message;
        public ParseError(int start, int end, String message) {
            this.start = start;
            this.end = end;
            this.message = message;
        }
    }

    public List<ParseError> getErrores() {
        return errores;
    }

    public ASTNode parse(List<TokenManager.TokenEntry> tokens) {
        this.tokens = tokens;
        this.indice = 0;
        this.errores.clear();
        this.ultimoTokenConsumido = null;

        try {
            return programa();
        } catch (Exception e) {
            return null;
        }
    }

    private TokenManager.TokenEntry actual() {
        if (indice < tokens.size()) return tokens.get(indice);
        return null;
    }

    private String actualToken() {
        TokenManager.TokenEntry t = actual();
        return t != null ? t.token() : "EOF";
    }

    private boolean match(String esperado) {
        if (indice < tokens.size() && tokens.get(indice).token().equals(esperado)) {
            ultimoTokenConsumido = tokens.get(indice);
            indice++;
            return true;
        }
        return false;
    }

    private void consumir(String esperado, String mensajeError, Set<String> follow) {
        if (match(esperado)) return;

        TokenManager.TokenEntry t = actual();
        int start, end;
        if (t != null) {
            start = t.start();
            end = t.end();
        } else if (ultimoTokenConsumido != null) {
            start = ultimoTokenConsumido.end();
            end = start + 1;
        } else {
            start = 0;
            end = 1;
        }
        errores.add(new ParseError(start, end, mensajeError));

        while (indice < tokens.size() && !follow.contains(actualToken())) {
            indice++;
        }
    }

    private boolean esUnoDe(Set<String> conjunto) {
        return conjunto.contains(actualToken());
    }

    private int posInicio() {
        return (indice < tokens.size()) ? tokens.get(indice).start() :
                (ultimoTokenConsumido != null ? ultimoTokenConsumido.end() : 0);
    }

    private int posFin() {
        return (ultimoTokenConsumido != null) ? ultimoTokenConsumido.end() : 0;
    }

    private ASTNode programa() {
        int start = posInicio();
        consumir("TK_START", "Se esperaba 'START'", FOLLOW_PROGRAMA);
        consumir("TK_{", "Se esperaba '{'", FOLLOW_CUERPO);
        List<ASTNode> statements = cuerpo();
        consumir("TK_}", "Se esperaba '}'", Set.of("TK_END"));
        consumir("TK_END", "Se esperaba 'END'", Set.of("EOF"));
        int end = posFin();
        return new ProgramNode(start, end, statements);
    }

    private List<ASTNode> cuerpo() {
        List<ASTNode> statements = new ArrayList<>();
        while (indice < tokens.size() && !esUnoDe(Set.of("TK_}", "TK_END"))) {
            if (esUnoDe(Set.of("TK_tipoEntero", "TK_tipoDecimal"))) {
                ASTNode declGroup = declaracion();
                if (declGroup instanceof DeclarationGroupNode) {
                    statements.addAll(((DeclarationGroupNode) declGroup).getDeclarations());
                }
            } else if (esUnoDe(Set.of("TK_ID", "TK_READ", "TK_PRINT"))) {
                ASTNode sent = sentencia();
                if (sent != null) statements.add(sent);
            } else {
                TokenManager.TokenEntry t = actual();
                errores.add(new ParseError(t.start(), t.end(), "Token inesperado: " + t.lexema()));
                indice++;
            }
        }
        return statements;
    }

    private ASTNode declaracion() {
        int start = posInicio();
        String tipo;
        if (match("TK_tipoEntero")) {
            tipo = "integer";
        } else if (match("TK_tipoDecimal")) {
            tipo = "decimal";
        } else {
            return null;
        }
        List<ASTNode> lista = listaIdent(tipo);
        if (lista.isEmpty()) {
            return new DeclarationGroupNode(start, posFin(), lista);
        }
        consumir("TK_;", "Falta ';' al final de la declaración", FOLLOW_DECLARACION);
        int end = posFin();
        return new DeclarationGroupNode(start, end, lista);
    }

    private List<ASTNode> listaIdent(String tipo) {
        List<ASTNode> nodos = new ArrayList<>();
        if (!match("TK_ID")) {
            int start = (ultimoTokenConsumido != null) ? ultimoTokenConsumido.end() : 0;
            int end = start + 1;
            TokenManager.TokenEntry t = actual();
            if (t != null) {
                end = t.start();
            }
            errores.add(new ParseError(start, end, "Se esperaba un identificador"));
            return nodos;
        }
        String id1 = ultimoTokenConsumido.lexema();
        int start1 = ultimoTokenConsumido.start();
        int end1 = ultimoTokenConsumido.end();
        if (match("TK_=")) {
            ASTNode expr = expresion();
            nodos.add(new DeclarationNode(start1, end1, tipo, id1, expr));
        } else {
            nodos.add(new DeclarationNode(start1, end1, tipo, id1, null));
        }
        while (match("TK_,")) {
            if (!match("TK_ID")) {
                TokenManager.TokenEntry t = actual();
                int start = (t != null) ? t.start() : (ultimoTokenConsumido != null ? ultimoTokenConsumido.end() : 0);
                int end = (t != null) ? t.end() : start + 1;
                errores.add(new ParseError(start, end, "Se esperaba un identificador después de ','"));

                while (indice < tokens.size() && !FOLLOW_LISTA_IDENT.contains(actualToken())
                        && !Set.of("TK_tipoEntero", "TK_tipoDecimal", "TK_ID", "TK_READ", "TK_PRINT", "TK_}").contains(actualToken())) {
                    indice++;
                }
                if (actualToken().equals("TK_,")) {
                    continue;
                } else {
                    break;
                }
            }
            String id = ultimoTokenConsumido.lexema();
            int start = ultimoTokenConsumido.start();
            int end = ultimoTokenConsumido.end();
            if (match("TK_=")) {
                ASTNode expr = expresion();
                nodos.add(new DeclarationNode(start, end, tipo, id, expr));
            } else {
                nodos.add(new DeclarationNode(start, end, tipo, id, null));
            }
        }
        return nodos;
    }

    private ASTNode sentencia() {
        if (match("TK_ID")) {
            String id = ultimoTokenConsumido.lexema();
            int start = ultimoTokenConsumido.start();
            consumir("TK_=", "Se esperaba '=' en asignación", FOLLOW_SENTENCIA);
            ASTNode expr = expresion();
            consumir("TK_;", "Falta ';' al final de la asignación", FOLLOW_SENTENCIA);
            int end = posFin();
            return new AssignmentNode(start, end, id, expr);
        } else if (match("TK_READ")) {
            int start = ultimoTokenConsumido.start();
            consumir("TK_(", "Se esperaba '(' después de READ", FOLLOW_SENTENCIA);
            if (!match("TK_ID")) {
                TokenManager.TokenEntry t = actual();
                errores.add(new ParseError(t.start(), t.end(), "Se esperaba un identificador en READ"));
            }
            String id = (ultimoTokenConsumido != null && ultimoTokenConsumido.token().equals("TK_ID"))
                    ? ultimoTokenConsumido.lexema() : "";
            consumir("TK_)", "Se esperaba ')' después del identificador", FOLLOW_SENTENCIA);
            consumir("TK_;", "Falta ';' después de READ", FOLLOW_SENTENCIA);
            int end = posFin();
            return new ReadNode(start, end, id);
        } else if (match("TK_PRINT")) {
            int start = ultimoTokenConsumido.start();
            consumir("TK_(", "Se esperaba '(' después de PRINT", FOLLOW_SENTENCIA);
            ASTNode expr = expresion();
            consumir("TK_)", "Se esperaba ')' después de la expresión", FOLLOW_SENTENCIA);
            consumir("TK_;", "Falta ';' después de PRINT", FOLLOW_SENTENCIA);
            int end = posFin();
            return new PrintNode(start, end, expr);
        } else {
            TokenManager.TokenEntry t = actual();
            errores.add(new ParseError(t.start(), t.end(), "Se esperaba una sentencia (identificador, READ o PRINT)"));
            while (indice < tokens.size() && !Set.of("TK_;", "TK_}").contains(actualToken())) {
                indice++;
            }
            return null;
        }
    }

    private ASTNode expresion() {
        ASTNode left = termino();
        while (match("TK_+") || match("TK_-")) {
            String op = ultimoTokenConsumido.lexema();
            int start = left.getStart();
            ASTNode right = termino();
            int end = right.getEnd();
            left = new BinaryOpNode(start, end, op, left, right);
        }
        return left;
    }

    private ASTNode termino() {
        ASTNode left = factor();
        while (match("TK_*") || match("TK_/")) {
            String op = ultimoTokenConsumido.lexema();
            int start = left.getStart();
            ASTNode right = factor();
            int end = right.getEnd();
            left = new BinaryOpNode(start, end, op, left, right);
        }
        return left;
    }

    private ASTNode factor() {
        if (match("TK_ID")) {
            return new IdentifierNode(ultimoTokenConsumido.start(), ultimoTokenConsumido.end(), ultimoTokenConsumido.lexema());
        } else if (match("TK_ENT")) {
            return new LiteralNode(ultimoTokenConsumido.start(), ultimoTokenConsumido.end(), ultimoTokenConsumido.lexema(), true);
        } else if (match("TK_DEC")) {
            return new LiteralNode(ultimoTokenConsumido.start(), ultimoTokenConsumido.end(), ultimoTokenConsumido.lexema(), false);
        } else if (match("TK_(")) {
            int start = ultimoTokenConsumido.start();
            ASTNode inner = expresion();
            consumir("TK_)", "Se esperaba ')' después de la expresión", FOLLOW_FACTOR);
            int end = posFin();
            return inner;
        } else {
            TokenManager.TokenEntry t = actual();
            if (t != null) {
                errores.add(new ParseError(t.start(), t.end(), "Se esperaba un identificador, número o '('"));
                while (indice < tokens.size() && !FOLLOW_EXPRESION.contains(actualToken())) {
                    indice++;
                }
            }
            return new LiteralNode(t != null ? t.start() : 0, t != null ? t.end() : 1, "0", true);
        }
    }

    public static class DeclarationGroupNode extends ASTNode {
        private final List<ASTNode> declarations;
        public DeclarationGroupNode(int start, int end, List<ASTNode> declarations) {
            super(start, end);
            this.declarations = declarations;
        }
        public List<ASTNode> getDeclarations() { return declarations; }
    }
}