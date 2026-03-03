package com.modelo.lr;

import com.controlador.TokenManager;
import com.modelo.SyntaxTreeNodeRow;
import com.modelo.Tokenizer;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ShiftReduceParseTreeBuilderTest {

    /** Tokenise source text using the real Tokenizer + the same token-name mapping as the app. */
    private static List<TokenManager.TokenEntry> tokenize(String src) {
        List<TokenManager.TokenEntry> result = new ArrayList<>();
        Tokenizer.scan(src, new Tokenizer.TokenSink() {
            @Override
            public void onToken(Tokenizer.Token t) {
                result.add(new TokenManager.TokenEntry(t.value, mapToken(t.type, t.value), t.start, t.end));
            }
            @Override
            public void onError(Tokenizer.TokenError e) { /* ignore lex errors in tree test */ }
        });
        return result;
    }

    private static String mapToken(String type, String lexema) {
        return switch (type) {
            case "PR" -> switch (lexema.toUpperCase(Locale.ROOT)) {
                case "INTEGER" -> "TK_tipoEntero";
                case "DECIMAL" -> "TK_tipoDecimal";
                default -> "TK_" + lexema.toUpperCase(Locale.ROOT);
            };
            case "ID"   -> "TK_ID";
            case "ENT"  -> "TK_ENT";
            case "DEC"  -> "TK_DEC";
            case "SIMB" -> "TK_" + lexema;
            default     -> "TK_" + type;
        };
    }

    // -------------------------------------------------------------------------

    private static final String SAMPLE =
            "START{ \n" +
            "INTEGER a1, a2=20, a3; \n" +
            "DECIMAL b21, b22, b23=2.5; \n" +
            "READ (a1); \n" +
            "READ (b21); \n" +
            "a3=a1+a2; \n" +
            "a2=(a1-30)*a3; \n" +
            "b22=b21*b23/5.0; \n" +
            "PRINT (a2); \n" +
            "PRINT (b22); \n" +
            "}END";

    @Test
    void sampleParsesWithoutErrors() {
        List<TokenManager.TokenEntry> tokens = tokenize(SAMPLE);
        assertFalse(tokens.isEmpty(), "Token list must not be empty");

        List<SyntaxTreeNodeRow> rows = new ShiftReduceParseTreeBuilder().build(tokens);
        assertFalse(rows.isEmpty(), "Parse tree must not be empty");
    }

    @Test
    void rootIsPROGRAMAWithNullParent() {
        List<SyntaxTreeNodeRow> rows = new ShiftReduceParseTreeBuilder().build(tokenize(SAMPLE));
        SyntaxTreeNodeRow root = rows.get(0);
        assertEquals("PROGRAMA", root.getToken(), "First row must be PROGRAMA");
        assertNull(root.getParentId(), "PROGRAMA parent must be NULL");
    }

    @Test
    void inicioIsChildOfPROGRAMA() {
        List<SyntaxTreeNodeRow> rows = new ShiftReduceParseTreeBuilder().build(tokenize(SAMPLE));
        int programaId = rows.get(0).getNodeId();
        assertTrue(rows.stream()
                .anyMatch(r -> "INICIO".equals(r.getToken()) && Integer.valueOf(programaId).equals(r.getParentId())),
                "INICIO must be a direct child of PROGRAMA");
    }

    @Test
    void startTokenLabelIsTkStart() {
        List<SyntaxTreeNodeRow> rows = new ShiftReduceParseTreeBuilder().build(tokenize(SAMPLE));
        int inicioId = rows.stream()
                .filter(r -> "INICIO".equals(r.getToken()))
                .mapToInt(SyntaxTreeNodeRow::getNodeId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("INICIO node not found"));
        assertTrue(rows.stream()
                .anyMatch(r -> "TK_START".equals(r.getToken()) && Integer.valueOf(inicioId).equals(r.getParentId())),
                "TK_START token must be a child of INICIO");
    }

    @Test
    void declaracionNodesExist() {
        List<SyntaxTreeNodeRow> rows = new ShiftReduceParseTreeBuilder().build(tokenize(SAMPLE));
        long count = rows.stream().filter(r -> "DECLARACION".equals(r.getToken())).count();
        assertTrue(count >= 2, "Should have at least 2 DECLARACION nodes (INTEGER + DECIMAL)");
    }

    @Test
    void lecturaAndEscrituraNodesExist() {
        List<SyntaxTreeNodeRow> rows = new ShiftReduceParseTreeBuilder().build(tokenize(SAMPLE));
        assertTrue(rows.stream().anyMatch(r -> "LECTURA".equals(r.getToken())),
                "LECTURA node must exist");
        assertTrue(rows.stream().anyMatch(r -> "ESCRITURA".equals(r.getToken())),
                "ESCRITURA node must exist");
    }

    @Test
    void emptyInputReturnsTreeWithPROGRAMA() {
        List<SyntaxTreeNodeRow> rows = new ShiftReduceParseTreeBuilder().build(Collections.emptyList());
        assertFalse(rows.isEmpty());
        assertEquals("PROGRAMA", rows.get(0).getToken());
    }

    @Test
    void allNodeIdsAreUnique() {
        List<SyntaxTreeNodeRow> rows = new ShiftReduceParseTreeBuilder().build(tokenize(SAMPLE));
        Set<Integer> ids = new HashSet<>();
        for (SyntaxTreeNodeRow r : rows) {
            assertTrue(ids.add(r.getNodeId()), "Duplicate node id: " + r.getNodeId());
        }
    }

    @Test
    void parentIdsReferToExistingNodes() {
        List<SyntaxTreeNodeRow> rows = new ShiftReduceParseTreeBuilder().build(tokenize(SAMPLE));
        Set<Integer> ids = new HashSet<>();
        for (SyntaxTreeNodeRow r : rows) ids.add(r.getNodeId());
        for (SyntaxTreeNodeRow r : rows) {
            if (r.getParentId() != null) {
                assertTrue(ids.contains(r.getParentId()),
                        "Node " + r.getNodeId() + " (" + r.getToken()
                        + ") references unknown parent " + r.getParentId());
            }
        }
    }

    @Test
    void expressionNodesUseGrammarSymbols() {
        List<SyntaxTreeNodeRow> rows = new ShiftReduceParseTreeBuilder().build(tokenize(SAMPLE));
        assertTrue(rows.stream().anyMatch(r -> "E".equals(r.getToken())), "E nodes must exist");
        assertTrue(rows.stream().anyMatch(r -> "T".equals(r.getToken())), "T nodes must exist");
        assertTrue(rows.stream().anyMatch(r -> "F".equals(r.getToken())), "F nodes must exist");
    }

    @Test
    void tokenNodesHaveTkPrefixLabels() {
        List<SyntaxTreeNodeRow> rows = new ShiftReduceParseTreeBuilder().build(tokenize(SAMPLE));
        // All terminal (leaf) nodes must use TK_* labels
        Set<String> nonTerminals = Set.of(
                "PROGRAMA", "INICIO", "FINAL", "CUERPO", "LISTA_INSTRUCCIONES", "INSTRUCCION",
                "DECLARACION", "LISTAIDS", "ELEMENTOID", "ASIGNACION",
                "LECTURA", "ESCRITURA", "E", "T", "F");
        for (SyntaxTreeNodeRow r : rows) {
            String label = r.getToken();
            if (!nonTerminals.contains(label)) {
                assertTrue(label.startsWith("TK_"),
                        "Leaf node label must start with TK_: '" + label + "'");
            }
        }
    }

    @Test
    void tokenNodesHaveLowerIdsThanParents() {
        List<SyntaxTreeNodeRow> rows = new ShiftReduceParseTreeBuilder().build(tokenize(SAMPLE));
        Map<Integer, Integer> idToParent = new HashMap<>();
        for (SyntaxTreeNodeRow r : rows) {
            if (r.getParentId() != null) {
                idToParent.put(r.getNodeId(), r.getParentId());
            }
        }
        // Every node's ID must be less than its parent's ID (shift/reduce order)
        for (Map.Entry<Integer, Integer> e : idToParent.entrySet()) {
            int childId  = e.getKey();
            int parentId = e.getValue();
            assertTrue(childId < parentId,
                    "Child ID " + childId + " must be less than parent ID " + parentId
                    + " (shift/reduce order)");
        }
    }

    @Test
    void asignacionNodesExist() {
        List<SyntaxTreeNodeRow> rows = new ShiftReduceParseTreeBuilder().build(tokenize(SAMPLE));
        long count = rows.stream().filter(r -> "ASIGNACION".equals(r.getToken())).count();
        assertTrue(count >= 3, "Should have at least 3 ASIGNACION nodes");
    }
}
