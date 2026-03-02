package com.modelo.parser;

import com.controlador.TokenManager;
import com.modelo.SyntaxTreeNodeRow;
import com.modelo.Tokenizer;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ParseTreeBuilderTest {

    /** Build a TokenEntry list from raw source text using the real Tokenizer. */
    private static List<TokenManager.TokenEntry> tokenize(String src) {
        List<TokenManager.TokenEntry> result = new ArrayList<>();
        Tokenizer.scan(src, new Tokenizer.TokenSink() {
            @Override
            public void onToken(Tokenizer.Token t) {
                String tk = mapToken(t.type, t.value);
                result.add(new TokenManager.TokenEntry(t.value, tk, t.start, t.end));
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

        List<SyntaxTreeNodeRow> rows = new ParseTreeBuilder().build(tokens);
        assertFalse(rows.isEmpty(), "Parse tree must not be empty");
    }

    @Test
    void rootIsPROGRAMAWithParentZero() {
        List<SyntaxTreeNodeRow> rows = new ParseTreeBuilder().build(tokenize(SAMPLE));
        SyntaxTreeNodeRow root = rows.get(0);
        assertEquals("PROGRAMA", root.getToken(), "First row must be PROGRAMA");
        assertEquals(0, root.getParentId(), "PROGRAMA parent must be 0");
    }

    @Test
    void inicioIsChildOfPROGRAMA() {
        List<SyntaxTreeNodeRow> rows = new ParseTreeBuilder().build(tokenize(SAMPLE));
        int programaId = rows.get(0).getNodeId();
        assertTrue(rows.stream()
                .anyMatch(r -> "INICIO".equals(r.getToken()) && r.getParentId() == programaId),
                "INICIO must be a direct child of PROGRAMA");
    }

    @Test
    void startTokenIsChildOfINICIO() {
        List<SyntaxTreeNodeRow> rows = new ParseTreeBuilder().build(tokenize(SAMPLE));
        int inicioId = rows.stream()
                .filter(r -> "INICIO".equals(r.getToken()))
                .mapToInt(SyntaxTreeNodeRow::getNodeId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("INICIO node not found"));
        assertTrue(rows.stream()
                .anyMatch(r -> "START".equals(r.getToken()) && r.getParentId() == inicioId),
                "START token must be a child of INICIO");
    }

    @Test
    void declaracionNodesExist() {
        List<SyntaxTreeNodeRow> rows = new ParseTreeBuilder().build(tokenize(SAMPLE));
        long count = rows.stream().filter(r -> "DECLARACION".equals(r.getToken())).count();
        assertTrue(count >= 2, "Should have at least 2 DECLARACION nodes (INTEGER + DECIMAL)");
    }

    @Test
    void lecturaAndEscrituraNodesExist() {
        List<SyntaxTreeNodeRow> rows = new ParseTreeBuilder().build(tokenize(SAMPLE));
        assertTrue(rows.stream().anyMatch(r -> "LECTURA".equals(r.getToken())),
                "LECTURA node must exist");
        assertTrue(rows.stream().anyMatch(r -> "ESCRITURA".equals(r.getToken())),
                "ESCRITURA node must exist");
    }

    @Test
    void emptyInputReturnsTree() {
        // Even an empty token list must not throw and must return rows containing PROGRAMA
        List<SyntaxTreeNodeRow> rows = new ParseTreeBuilder().build(Collections.emptyList());
        assertFalse(rows.isEmpty());
        assertEquals("PROGRAMA", rows.get(0).getToken());
    }

    @Test
    void allNodeIdsAreUnique() {
        List<SyntaxTreeNodeRow> rows = new ParseTreeBuilder().build(tokenize(SAMPLE));
        Set<Integer> ids = new HashSet<>();
        for (SyntaxTreeNodeRow r : rows) {
            assertTrue(ids.add(r.getNodeId()), "Duplicate node id: " + r.getNodeId());
        }
    }

    @Test
    void parentIdsReferToExistingNodes() {
        List<SyntaxTreeNodeRow> rows = new ParseTreeBuilder().build(tokenize(SAMPLE));
        Set<Integer> ids = new HashSet<>();
        for (SyntaxTreeNodeRow r : rows) ids.add(r.getNodeId());
        for (SyntaxTreeNodeRow r : rows) {
            if (r.getParentId() != 0) {
                assertTrue(ids.contains(r.getParentId()),
                        "Node " + r.getNodeId() + " (" + r.getToken()
                        + ") references unknown parent " + r.getParentId());
            }
        }
    }

    @Test
    void expressionNodesUseGrammarSymbols() {
        List<SyntaxTreeNodeRow> rows = new ParseTreeBuilder().build(tokenize(SAMPLE));
        assertTrue(rows.stream().anyMatch(r -> "E".equals(r.getToken())), "E nodes must exist");
        assertTrue(rows.stream().anyMatch(r -> "T".equals(r.getToken())), "T nodes must exist");
        assertTrue(rows.stream().anyMatch(r -> "F".equals(r.getToken())), "F nodes must exist");
    }

    // -------------------------------------------------------------------------
    // Token display format tests (Format A vs Format B)
    // -------------------------------------------------------------------------

    @Test
    void defaultFormatUsesRawLexemes() {
        // Format B (default): identifiers and numbers appear as bare lexemes
        List<SyntaxTreeNodeRow> rows = new ParseTreeBuilder().build(tokenize(SAMPLE));
        assertTrue(rows.stream().anyMatch(r -> "a1".equals(r.getToken())),
                "Format B: identifier 'a1' must appear as raw lexeme");
        assertTrue(rows.stream().anyMatch(r -> "20".equals(r.getToken())),
                "Format B: integer '20' must appear as raw lexeme");
        assertFalse(rows.stream().anyMatch(r -> r.getToken().startsWith("id(")),
                "Format B: no token must be wrapped in id(...)");
        assertFalse(rows.stream().anyMatch(r -> r.getToken().startsWith("num(")),
                "Format B: no token must be wrapped in num(...)");
    }

    @Test
    void formatAWrapsIdentifiersAndNumbers() {
        // Format A (wrapIdAndNum=true): TK_ID → id(...), TK_ENT/TK_DEC → num(...)
        List<SyntaxTreeNodeRow> rows = new ParseTreeBuilder().build(tokenize(SAMPLE), true);
        assertTrue(rows.stream().anyMatch(r -> "id(a1)".equals(r.getToken())),
                "Format A: identifier 'a1' must be wrapped as 'id(a1)'");
        assertTrue(rows.stream().anyMatch(r -> "num(20)".equals(r.getToken())),
                "Format A: integer '20' must be wrapped as 'num(20)'");
        assertTrue(rows.stream().anyMatch(r -> "num(2.5)".equals(r.getToken())),
                "Format A: decimal '2.5' must be wrapped as 'num(2.5)'");
        assertFalse(rows.stream().anyMatch(r -> "a1".equals(r.getToken())),
                "Format A: bare identifier 'a1' must not appear unwrapped");
    }

    @Test
    void formatAKeepsPunctuationAndKeywordsAsIs() {
        // Punctuation and keywords must not be wrapped regardless of the flag
        List<SyntaxTreeNodeRow> rows = new ParseTreeBuilder().build(tokenize(SAMPLE), true);
        assertTrue(rows.stream().anyMatch(r -> "START".equals(r.getToken())),
                "Format A: START keyword must remain as-is");
        assertTrue(rows.stream().anyMatch(r -> "{".equals(r.getToken())),
                "Format A: '{' must remain as-is");
        assertTrue(rows.stream().anyMatch(r -> ";".equals(r.getToken())),
                "Format A: ';' must remain as-is");
    }

    @Test
    void formatBExplicitFlagMatchesDefault() {
        List<TokenManager.TokenEntry> tokens = tokenize(SAMPLE);
        List<SyntaxTreeNodeRow> defaultRows = new ParseTreeBuilder().build(tokens);
        List<SyntaxTreeNodeRow> explicitRows = new ParseTreeBuilder().build(tokens, false);
        assertEquals(defaultRows.size(), explicitRows.size(),
                "Explicit Format B must produce same number of rows as default");
        for (int i = 0; i < defaultRows.size(); i++) {
            assertEquals(defaultRows.get(i).getToken(), explicitRows.get(i).getToken(),
                    "Row " + i + " token must match between default and explicit Format B");
        }
    }
}
